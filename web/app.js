/* 行思录：无第三方依赖、无联网请求的交互预览。 */
(() => {
  'use strict';
  const STORAGE_KEY = 'xingsilu.preview.v1';
  const $ = id => document.getElementById(id);
  const icon = name => `<svg aria-hidden="true"><use href="#i-${name}"/></svg>`;
  const escapeHTML = text => String(text).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const uid = () => globalThis.crypto?.randomUUID?.() || `note-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  const stamp = () => new Date().toISOString();
  const dateText = (value, time = false) => new Date(value).toLocaleString('zh-CN', {year:'numeric',month:'2-digit',day:'2-digit',...(time ? {hour:'2-digit',minute:'2-digit',hour12:false} : {})});
  const localDate = () => { const d = new Date(); return `${d.getFullYear()}${String(d.getMonth()+1).padStart(2,'0')}${String(d.getDate()).padStart(2,'0')}`; };
  const labels = {experience:'经验',idea:'想法'};
  let toastTimer, saveTimer, confirmCallback, storageBroken = false;
  let tab = 'experience', query = '', tagFilter = '', selectedId = null, draft = null, dirty = false;

  function makeSamples() {
    const base = {tags:[],isPinned:false,isDeleted:false,eventDate:'',isSample:true};
    const note = (id,type,summary,content,tags,date,pin=false) => ({...base,id,type,summary,content,tags,createdAt:date,updatedAt:date,isPinned:pin});
    return {schemaVersion:1,theme:'light',sort:'updatedAt',records:[
      note('sample-e1','experience','遇到困难时，先拆解问题，再向前一步。','有时候，让人停在原地的不是问题本身，而是“一定要一次做好”的念头。\n\n试着把一件大事拆成几件小事，先完成最清楚、最容易着手的那一步。开始行动以后，原本模糊的方向，也会慢慢清晰起来。\n\n不必一下子看见终点，先走好眼前这一小段路。',['自我成长','做事方法'],'2026-10-22T09:20:00+08:00',true),
      note('sample-e2','experience','不要轻易相信未经验证的信息。','看到一个说法时，先问一问：它来自哪里？有没有原始资料？\n\n给判断留一点余地，也给自己留一点核实的时间。',['独立思考'],'2026-10-21T20:10:00+08:00'),
      note('sample-e3','experience','向别人承诺之前，先评估自己的能力。','热情值得珍惜，但时间和精力也有边界。\n\n认真衡量以后再答应，比事后勉强兑现更负责任。',['人际相处'],'2026-10-19T19:30:00+08:00'),
      note('sample-e4','experience','允许自己慢一点，但不要停止生长。','',[],'2026-10-17T12:00:00+08:00'),
      note('sample-i1','idea','','能不能做一张中国铁路发展的动态地图？\n\n从 2000 年开始，用时间轴让一条条线路慢慢亮起来。让“发展”不只是一个数字，而是看得见的连接。',['创作灵感'],'2026-10-22T11:10:00+08:00',true),
      note('sample-i2','idea','','把突然想到的事先记下来，不急着判断它有没有用。\n\n也许过些时候，它会和另一件事连在一起。',['日常思考'],'2026-10-21T17:20:00+08:00'),
      note('sample-i3','idea','','周末留一点时间散步，不带任务，只看看树和天空。',[],'2026-10-20T08:00:00+08:00')
    ]};
  }

  function validRecord(r) {
    return r && typeof r.id === 'string' && r.id.length > 0 && ['experience','idea'].includes(r.type)
      && typeof r.summary === 'string' && typeof r.content === 'string'
      && Array.isArray(r.tags) && r.tags.every(t => typeof t === 'string')
      && typeof r.isPinned === 'boolean' && typeof r.isDeleted === 'boolean'
      && typeof r.createdAt === 'string' && Number.isFinite(Date.parse(r.createdAt))
      && typeof r.updatedAt === 'string' && Number.isFinite(Date.parse(r.updatedAt))
      && (r.eventDate === undefined || (typeof r.eventDate === 'string' && (r.eventDate === '' || /^\d{4}-\d{2}-\d{2}$/.test(r.eventDate))))
      && (r.type === 'experience' ? r.summary.trim().length > 0 : r.content.trim().length > 0);
  }
  function validBackup(data) {
    return data && data.schemaVersion === 1 && Array.isArray(data.records)
      && data.records.every(validRecord) && new Set(data.records.map(r => r.id)).size === data.records.length;
  }
  function load() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw === null) return makeSamples();
      const parsed = JSON.parse(raw);
      if (!validBackup(parsed)) throw new Error('Invalid local data');
      return parsed;
    } catch {
      storageBroken = true;
      return {schemaVersion:1,theme:'light',sort:'updatedAt',records:[]};
    }
  }
  let state = load();
  state.showDates = state.showDates === true;
  function persist() {
    if (storageBroken) { $('save-status').textContent = '未写入本地'; toast('本地存储不可用。请导出备份，避免关闭后丢失记录。'); return false; }
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(state)); return true; }
    catch { $('save-status').textContent = '未保存，请备份'; toast('保存失败，存储空间可能不足。请先导出备份。'); return false; }
  }
  function toast(message) {
    clearTimeout(toastTimer); $('toast').textContent = message; $('toast').hidden = false;
    toastTimer = setTimeout(() => { $('toast').hidden = true; }, 4000);
  }
  function ask(title, message, ok, action) {
    $('confirm-title').textContent = title; $('confirm-message').textContent = message;
    $('confirm-ok').textContent = ok; confirmCallback = action; $('confirm-dialog').showModal();
  }
  function applyTheme() {
    const mode = ['light','dark','system'].includes(state.theme) ? state.theme : 'light';
    document.documentElement.dataset.theme = mode === 'system' ? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark':'light') : mode;
    document.querySelectorAll('[data-theme]').forEach(b => { if (b.tagName === 'BUTTON') { b.classList.toggle('active',b.dataset.theme === mode); b.setAttribute('aria-pressed',String(b.dataset.theme === mode)); } });
  }
  function recordsForView() {
    const needle = query.trim().toLocaleLowerCase();
    const key = state.sort === 'createdAt' ? 'createdAt' : 'updatedAt';
    return state.records.filter(r => !r.isDeleted && r.type === tab
      && (!tagFilter || r.tags.includes(tagFilter))
      && (!needle || `${r.summary}\n${r.content}\n${r.tags.join(' ')}`.toLocaleLowerCase().includes(needle)))
      .sort((a,b) => Number(b.isPinned)-Number(a.isPinned) || Date.parse(b[key])-Date.parse(a[key]));
  }
  function applyDatePreference() {
    $('show-dates').checked = state.showDates;
    $('note-date').hidden = !state.showDates;
    $('note-meta').hidden = !state.showDates && $('sample-label').hidden;
  }
  function renderFilters() {
    const tags = [...new Set(state.records.filter(r => r.type === tab && !r.isDeleted).flatMap(r => r.tags))];
    if (tagFilter && !tags.includes(tagFilter)) tagFilter = '';
    $('tag-filters').innerHTML = [{name:'全部',value:''},...tags.map(name => ({name,value:name}))]
      .map(t => `<button data-filter="${escapeHTML(t.value)}" class="${tagFilter === t.value ? 'active':''}" aria-pressed="${tagFilter === t.value}">${escapeHTML(t.name)}</button>`).join('');
  }
  function renderList() {
    renderFilters();
    const list = recordsForView();
    $('records').innerHTML = list.length ? list.map(r => `<button class="record ${r.id === selectedId ? 'selected':''}" data-record="${escapeHTML(r.id)}" aria-pressed="${r.id === selectedId}"><div class="record-title">${escapeHTML((r.type === 'experience' ? r.summary : r.content).replace(/\s+/g,' ').trim())}</div><div class="record-meta">${r.isPinned ? icon('pin'):''}${state.showDates ? `<time datetime="${escapeHTML(r.createdAt)}">${dateText(r.createdAt)}</time>`:''}${r.tags.slice(0,2).map(t => `<span class="tag">${escapeHTML(t)}</span>`).join('')}</div></button>`).join('')
      : `<div class="empty-list">${icon(query || tagFilter ? 'search' : tab === 'idea' ? 'spark':'book')}<div>${query || tagFilter ? '没有找到这条记录':'这里，留给你的第一条' + labels[tab]}</div><p>${query || tagFilter ? '换个词，或者试试其他标签。':'从此刻的一句话开始。'}</p></div>`;
  }
  function resizeTextareas() {
    for (const el of [$('summary'),$('content')]) {
      if (el.hidden) continue;
      el.style.height = 'auto'; el.style.height = `${Math.max(el.scrollHeight, el.id === 'summary' ? 48 : (tab === 'idea' ? 340 : 230))}px`;
    }
  }
  function updateEditorMeta() {
    if (!draft) return;
    const count = [...((draft.type === 'experience' ? draft.summary : '') + draft.content).replace(/\s/g,'')].length;
    $('word-count').textContent = `${count} 字`;
    $('record-times').textContent = `创建于 ${dateText(draft.createdAt,true)} · 更新于 ${dateText(draft.updatedAt,true)}`;
    $('pin-record').classList.toggle('active',draft.isPinned);
    $('pin-record').setAttribute('aria-label',draft.isPinned ? '取消置顶':'置顶记录');
    $('pin-record').title = draft.isPinned ? '取消置顶':'置顶';
  }
  function renderEditor() {
    const hasNote = !!draft;
    $('editor-paper').hidden = !hasNote; $('editor-empty').hidden = hasNote;
    document.querySelectorAll('.editor-actions button').forEach(b => { b.disabled = !hasNote || !selectedId; });
    $('editor').classList.toggle('idea-mode',tab === 'idea');
    $('editor-kind').innerHTML = icon(tab === 'idea' ? 'spark':'book') + `<span>${tab === 'idea' ? '想法随记':'经验手记'}</span>`;
    if (!draft) { $('save-status').textContent = ''; $('word-count').textContent = '0 字'; return; }
    const isIdea = draft.type === 'idea';
    $('summary').hidden = isIdea; $('summary-label').hidden = isIdea;
    $('paper-divider').hidden = isIdea; $('content-label').hidden = isIdea;
    $('event-date-label').hidden = isIdea;
    $('summary').value = draft.summary; $('content').value = draft.content;
    $('content').placeholder = isIdea ? '此刻，脑海里浮现了什么？\n\n一个词、一句话，或一个还没想明白的问题。' : '发生了什么？你是怎么走过来的？\n\n随意写写就好，也可以以后再补充。';
    $('tags').value = draft.tags.join('，'); $('event-date').value = draft.eventDate || '';
    $('more-info').open = false;
    $('note-date').textContent = dateText(draft.createdAt,true);
    $('sample-label').hidden = !draft.isSample;
    applyDatePreference();
    $('first-save').hidden = isIdea || !!selectedId;
    $('save-status').textContent = selectedId ? (storageBroken ? '未写入本地':'已保存在本地') : (isIdea ? '输入即自动保存':'写好一句话，点击保存');
    updateEditorMeta(); requestAnimationFrame(resizeTextareas);
  }
  function flush(explicit = false) {
    clearTimeout(saveTimer);
    if (!draft || !dirty) return true;
    const isNew = !selectedId;
    // A new experience is created by its first explicit save; ideas save as typed.
    if (isNew && draft.type === 'experience' && !explicit) return true;
    if (draft.type === 'experience' ? !draft.summary.trim() : !draft.content.trim()) {
      $('save-status').textContent = draft.type === 'experience' ? '请保留一句话总结':'正文为空，尚未保存';
      return false;
    }
    const candidate = {...draft, summary:draft.summary.trim(), tags:[...new Set(draft.tags)], updatedAt:stamp()};
    if (isNew) candidate.id = uid();
    const priorRecords = state.records;
    state.records = isNew ? [...state.records,candidate] : state.records.map(r => r.id === selectedId ? candidate:r);
    if (!persist()) { state.records = priorRecords; return false; }
    draft = {...candidate,tags:[...candidate.tags]}; selectedId = candidate.id; dirty = false;
    $('first-save').hidden = true;
    document.querySelectorAll('.editor-actions button').forEach(b => { b.disabled = false; });
    $('save-status').textContent = '已保存在本地';
    updateEditorMeta(); renderList();
    return true;
  }
  function beforeLeave(action) {
    clearTimeout(saveTimer);
    if (dirty && !selectedId && draft?.type === 'experience' && (draft.summary.trim() || draft.content.trim())) {
      if (draft.summary.trim()) { if (!flush(true)) return; }
      else { ask('这条经验还没有总结','补充一句话即可保存；也可以放弃这次未保存的输入。','放弃输入',() => { dirty = false; action(); }); return; }
    } else if (!flush()) {
      ask('当前修改尚未保存','记录不能为空，或本地存储暂时不可用。返回编辑可以继续修改；放弃后保留上次保存的版本。','放弃修改',() => { dirty = false; action(); }); return;
    }
    action();
  }
  function openRecord(id, mobile = true) {
    selectedId = id; const r = state.records.find(r => r.id === id && !r.isDeleted);
    draft = r ? {...r,tags:[...r.tags]} : null; dirty = false;
    if (mobile && draft) document.body.classList.add('editor-open');
    renderList(); renderEditor(); $('editor-paper').scrollTop = 0;
  }
  function newRecord() {
    beforeLeave(() => {
      selectedId = null; dirty = false;
      const at = stamp(); draft = {id:'',type:tab,summary:'',content:'',tags:[],eventDate:'',createdAt:at,updatedAt:at,isPinned:false,isDeleted:false,isSample:false};
      document.body.classList.add('editor-open'); renderList(); renderEditor();
      (tab === 'experience' ? $('summary') : $('content')).focus();
    });
  }
  function switchTab(next) {
    beforeLeave(() => {
      tab = next; query = ''; tagFilter = ''; $('search').value = '';
      document.body.classList.remove('editor-open');
      $('collection-title').innerHTML = `我的${labels[tab]}<span class="heading-dot">.</span>`;
      $('collection-eyebrow').textContent = tab === 'experience' ? '走过的路，都算数':'让一闪而过，有处可留';
      $('search').placeholder = tab === 'experience' ? '找一找，曾经的收获':'找一找，脑海中的灵光';
      $('new-record').innerHTML = icon('plus') + `<span>记一条${labels[tab]}</span>`;
      document.querySelectorAll('[data-tab]').forEach(b => { const active = b.dataset.tab === tab; b.classList.toggle('active',active); if(active) b.setAttribute('aria-current','page'); else b.removeAttribute('aria-current'); });
      openRecord(recordsForView()[0]?.id || null,false);
    });
  }
  function edited() {
    if (!draft) return;
    draft.summary = $('summary').value; draft.content = $('content').value;
    draft.tags = $('tags').value.split(/[,，、\n]/).map(t => t.trim()).filter(Boolean);
    draft.eventDate = $('event-date').value;
    dirty = true; updateEditorMeta(); resizeTextareas(); clearTimeout(saveTimer);
    if (!selectedId && tab === 'experience') { $('save-status').textContent = '写好一句话，点击保存'; return; }
    $('save-status').textContent = '正在保存…'; saveTimer = setTimeout(() => flush(),450);
  }
  function download(name,content,type) {
    const url = URL.createObjectURL(new Blob([content],{type}));
    const a = document.createElement('a'); a.href = url; a.download = name;
    document.body.append(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url),10000);
  }
  function toMarkdown(records,type) {
    return `# 我的${labels[type]}记录\n\n` + [...records].sort((a,b) => Date.parse(a.createdAt)-Date.parse(b.createdAt)).map(r => {
      let s = type === 'experience' ? `## ${r.summary.replace(/\r?\n/g,' ')}\n\n` : `## ${dateText(r.createdAt,true)}\n\n`;
      s += `记录时间：${dateText(r.createdAt,true)}\n`;
      if(r.tags.length) s += `标签：${r.tags.join('、')}\n`;
      if(r.eventDate) s += `相关日期：${r.eventDate}\n`;
      s += '\n';
      if(type === 'experience' && r.content) s += '### 详情\n\n';
      return s + r.content + '\n\n------\n';
    }).join('\n');
  }
  function exportRecords(type, single = false) {
    if (!flush()) return;
    const notes = state.records.filter(r => !r.isDeleted && r.type === type && (!single || r.id === selectedId));
    if(!notes.length) { toast('还没有可导出的记录'); return; }
    download(`行思录-${labels[type]}${single ? '-单条':''}-${localDate()}.md`,toMarkdown(notes,type),'text/markdown;charset=utf-8'); toast('Markdown 文档已开始下载');
  }
  function renderTrash() {
    const notes = state.records.filter(r => r.isDeleted).sort((a,b) => Date.parse(b.updatedAt)-Date.parse(a.updatedAt));
    $('trash-list').innerHTML = notes.length ? notes.map(r => `<div class="trash-item"><p>${escapeHTML(r.type === 'experience' ? r.summary:r.content)}</p><div><small>${labels[r.type]} · ${dateText(r.createdAt)}</small><button data-recover="${escapeHTML(r.id)}">恢复</button><button data-purge="${escapeHTML(r.id)}">永久删除</button></div></div>`).join('') : '<div class="empty-list">回收站是空的。<p>删除的记录会先来到这里。</p></div>';
  }
  function mutateRecords(next) {
    const old = state.records; state.records = next;
    if(!persist()) { state.records = old; return false; }
    return true;
  }

  $('new-record').addEventListener('click',newRecord);
  document.querySelectorAll('[data-tab]').forEach(b => b.addEventListener('click',() => switchTab(b.dataset.tab)));
  $('records').addEventListener('click',e => { const b = e.target.closest('[data-record]'); if(b) beforeLeave(() => openRecord(b.dataset.record)); });
  $('search').addEventListener('input',() => { query = $('search').value; renderList(); });
  $('sort').value = state.sort === 'createdAt' ? 'createdAt':'updatedAt';
  $('sort').addEventListener('change',() => { state.sort = $('sort').value; persist(); renderList(); });
  $('filter-toggle').addEventListener('click',() => { $('tag-filters').hidden = !$('tag-filters').hidden; $('filter-toggle').setAttribute('aria-expanded',String(!$('tag-filters').hidden)); });
  $('tag-filters').addEventListener('click',e => { const b = e.target.closest('[data-filter]'); if(b) { tagFilter = b.dataset.filter; renderList(); } });
  ['summary','content','tags','event-date'].forEach(id => $(id).addEventListener('input',edited));
  $('first-save').addEventListener('click',() => {
    if(!draft.summary.trim()) { toast('先写一句话总结，就可以保存了'); $('summary').focus(); return; }
    if(flush(true)) toast('经验已保存，可以继续补充故事');
  });
  $('editor-back').addEventListener('click',() => beforeLeave(() => { document.body.classList.remove('editor-open'); if(!selectedId) openRecord(recordsForView()[0]?.id || null,false); }));
  $('pin-record').addEventListener('click',() => {
    if(!draft || !selectedId || !flush()) return;
    draft.isPinned = !draft.isPinned; dirty = true;
    if(flush()) toast(draft.isPinned ? '已置顶这条记录':'已取消置顶');
  });
  $('delete-record').addEventListener('click',() => {
    if(!selectedId || !flush()) return;
    const target = selectedId;
    ask('移入回收站？','这条记录可以随时在设置的回收站中恢复。','移入回收站',() => {
      if(!mutateRecords(state.records.map(r => r.id === target ? {...r,isDeleted:true,updatedAt:stamp()}:r))) return;
      dirty = false; document.body.classList.remove('editor-open'); openRecord(recordsForView()[0]?.id || null,false); toast('已移入回收站');
    });
  });
  $('export-record').addEventListener('click',() => exportRecords(tab,true));
  $('settings-open').addEventListener('click',() => beforeLeave(() => $('settings-dialog').showModal()));
  $('show-dates').addEventListener('change',() => {
    const previous = state.showDates;
    state.showDates = $('show-dates').checked;
    if (!persist()) state.showDates = previous;
    applyDatePreference();
    renderList();
  });
  document.querySelectorAll('[data-close]').forEach(b => b.addEventListener('click',() => $(b.dataset.close).close()));
  document.querySelectorAll('.theme-picker button').forEach(b => b.addEventListener('click',() => { state.theme = b.dataset.theme; applyTheme(); persist(); }));
  matchMedia('(prefers-color-scheme: dark)').addEventListener('change',applyTheme);
  document.querySelectorAll('[data-export]').forEach(b => b.addEventListener('click',() => exportRecords(b.dataset.export)));
  $('backup').addEventListener('click',() => {
    if(!flush()) return;
    download(`行思录-完整备份-${localDate()}.json`,JSON.stringify({...state,exportedAt:stamp()},null,2),'application/json;charset=utf-8'); toast('完整数据备份已开始下载');
  });
  $('restore').addEventListener('click',() => $('restore-file').click());
  $('restore-file').addEventListener('change',async () => {
    const file = $('restore-file').files[0]; $('restore-file').value = ''; if(!file) return;
    try {
      if(file.size > 10 * 1024 * 1024) throw new Error('large');
      const incoming = JSON.parse(await file.text()); if(!validBackup(incoming)) throw new Error('invalid');
      ask('合并这份备份？',`文件包含 ${incoming.records.length} 条记录。相同编号且内容相同的记录会跳过；有差异的记录将作为副本保留，不覆盖当前数据。`,'合并恢复',() => {
        const merged = [...state.records]; let added = 0;
        for(const item of incoming.records) {
          const record = {id:item.id,type:item.type,summary:item.summary,content:item.content,tags:[...new Set(item.tags)],eventDate:item.eventDate || '',createdAt:item.createdAt,updatedAt:item.updatedAt,isPinned:item.isPinned,isDeleted:item.isDeleted,isSample:item.isSample === true};
          const existing = merged.find(r => r.id === record.id);
          if(existing) {
            const fields = ['type','summary','content','tags','eventDate','createdAt','updatedAt','isPinned','isDeleted'];
            if(fields.every(key => JSON.stringify(existing[key] ?? '') === JSON.stringify(record[key] ?? ''))) continue;
            record.id = uid();
          }
          merged.push(record); added++;
        }
        if(!mutateRecords(merged)) return;
        renderList(); toast(`恢复完成，新增 ${added} 条记录`);
        if(!draft) openRecord(recordsForView()[0]?.id || null,false);
      });
    } catch { toast('无法读取备份：请使用行思录导出的有效 JSON 文件（不超过 10 MB）'); }
  });
  $('trash-open').addEventListener('click',() => { renderTrash(); $('trash-dialog').showModal(); });
  $('trash-list').addEventListener('click',e => {
    const recover = e.target.closest('[data-recover]');
    if(recover) {
      if(!mutateRecords(state.records.map(r => r.id === recover.dataset.recover ? {...r,isDeleted:false,updatedAt:stamp()}:r))) return;
      renderTrash(); renderList(); if(!draft) openRecord(recordsForView()[0]?.id || null,false); toast('记录已恢复');
    }
    const purge = e.target.closest('[data-purge]');
    if(purge) ask('永久删除这条记录？','删除后无法恢复。如有需要，请先导出完整备份。','永久删除',() => {
      if(mutateRecords(state.records.filter(r => r.id !== purge.dataset.purge))) { renderTrash(); toast('记录已永久删除'); }
    });
  });
  $('confirm-cancel').addEventListener('click',() => { confirmCallback = null; $('confirm-dialog').close(); });
  $('confirm-ok').addEventListener('click',() => { const action = confirmCallback; confirmCallback = null; $('confirm-dialog').close(); action?.(); });
  document.addEventListener('keydown',e => {
    const typing = ['INPUT','TEXTAREA','SELECT'].includes(document.activeElement.tagName);
    if(e.key === '/' && !typing && !document.querySelector('dialog[open]')) { e.preventDefault(); $('search').focus(); }
    if((e.ctrlKey || e.metaKey) && e.key === 'Enter' && draft && !document.querySelector('dialog[open]')) { e.preventDefault(); if(flush(true)) toast('已保存'); }
  });
  window.addEventListener('resize',resizeTextareas);
  document.addEventListener('visibilitychange',() => { if(document.visibilityState === 'hidden') flush(!!draft?.summary.trim()); });
  window.addEventListener('pagehide',() => flush(!!draft?.summary.trim()));
  window.addEventListener('beforeunload',e => { flush(!!draft?.summary.trim()); if(dirty && (draft?.summary.trim() || draft?.content.trim())) { e.preventDefault(); e.returnValue = ''; } });
  applyTheme();
  applyDatePreference();
  if(!storageBroken) persist();
  openRecord(recordsForView()[0]?.id || null,false);
  if(storageBroken) toast('无法读取本地存储，原数据未被覆盖。本次记录请及时导出备份。');
})();
