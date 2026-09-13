(function () {
  const state = {
    datasourceId: null,
    view: 'business',
    rows: [],
    ready: false,
    tab: 'person',
    scenarios: [],
    selectedScenarioId: null,
  };

  const $ = (id) => document.getElementById(id);

  async function api(path, options) {
    const res = await fetch('/playground' + path, Object.assign({
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
    }, options || {}));
    const json = await res.json();
    return { status: res.status, body: json };
  }

  function currentDs() {
    return $('datasourceSelect').value || state.datasourceId || null;
  }

  async function ensureAuth() {
    const check = await api('/api/check.json');
    const authEnabled = !!(check.body.data && check.body.data.authEnabled);
    const loggedIn = !!(check.body.data && check.body.data.loggedIn);
    $('logoutBtn').hidden = !authEnabled;
    if (authEnabled && !loggedIn) {
      $('loginPage').hidden = false;
      $('mainPage').hidden = true;
      return false;
    }
    $('loginPage').hidden = true;
    $('mainPage').hidden = false;
    return true;
  }

  async function loadDatasources() {
    const res = await api('/api/datasources.json');
    const list = (res.body.data || []);
    const select = $('datasourceSelect');
    select.innerHTML = '';
    list.forEach((item) => {
      const opt = document.createElement('option');
      opt.value = item.id;
      opt.textContent = item.id + (item.primary ? ' (主)' : '');
      select.appendChild(opt);
      if (item.primary) state.datasourceId = item.id;
    });
    if (!state.datasourceId && list.length) state.datasourceId = list[0].id;
    if (state.datasourceId) select.value = state.datasourceId;
  }

  async function loadPreflight() {
    const ds = currentDs();
    const q = ds ? ('?datasourceId=' + encodeURIComponent(ds)) : '';
    const res = await api('/api/person/preflight.json' + q);
    const data = res.body.data || {};
    state.ready = !!data.ready;
    const banner = $('preflightBanner');
    banner.classList.toggle('ready', state.ready);
    banner.classList.toggle('blocked', !state.ready);
    $('preflightTitle').textContent = state.ready ? '人员表配置就绪' : '人员表配置未就绪';
    $('preflightSummary').textContent = res.body.message || '';
    const ul = $('preflightChecks');
    ul.innerHTML = '';
    (data.checks || []).forEach((c) => {
      const li = document.createElement('li');
      li.textContent = (c.passed ? '✓ ' : '✗ ') + c.message;
      li.className = c.passed ? 'ok' : 'fail';
      ul.appendChild(li);
    });
  }

  function setView(view) {
    state.view = view;
    document.querySelectorAll('.view-btn').forEach((btn) => {
      btn.classList.toggle('active', btn.getAttribute('data-view') === view);
    });
    $('viewHint').textContent = view === 'raw'
      ? '原始视图：展示库内密文。请切回业务视图再编辑或删除。'
      : '业务视图：敏感字段已解密，可新增、编辑、删除。';
    $('createBtn').disabled = view === 'raw' || !state.ready;
  }

  async function loadList() {
    const payload = {
      datasourceId: currentDs(),
      view: state.view,
      name: $('filterName').value.trim() || null,
      phone: $('filterPhone').value.trim() || null,
      idCard: $('filterIdCard').value.trim() || null,
    };
    const res = await api('/api/person/list.json', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    const msg = $('tableMessage');
    if (!res.body.success) {
      msg.hidden = false;
      msg.textContent = res.body.message || '查询失败';
      state.rows = [];
      renderTable();
      return;
    }
    msg.hidden = true;
    state.rows = (res.body.data && res.body.data.rows) || [];
    renderTable();
  }

  function cell(value) {
    if (value == null || value === '') return '—';
    const text = String(value);
    return text.length > 48 ? text.slice(0, 48) + '…' : text;
  }

  function renderTable() {
    const tbody = $('personTableBody');
    tbody.innerHTML = '';
    if (!state.rows.length) {
      const tr = document.createElement('tr');
      tr.innerHTML = '<td colspan="7" class="empty">暂无数据</td>';
      tbody.appendChild(tr);
      return;
    }
    const mutateDisabled = state.view === 'raw';
    state.rows.forEach((row) => {
      const tr = document.createElement('tr');
      tr.innerHTML =
        '<td>' + cell(row.id) + '</td>' +
        '<td>' + cell(row.name) + '</td>' +
        '<td class="mono">' + cell(row.phone) + '</td>' +
        '<td class="mono">' + cell(row.idCard) + '</td>' +
        '<td>' + cell(row.age) + '</td>' +
        '<td class="mono">' + cell(row.row_digest) + '</td>' +
        '<td class="actions"></td>';
      const actions = tr.querySelector('.actions');
      const editBtn = document.createElement('button');
      editBtn.type = 'button';
      editBtn.className = 'btn btn-secondary btn-sm';
      editBtn.textContent = '编辑';
      editBtn.disabled = mutateDisabled;
      editBtn.addEventListener('click', () => openEdit(row));
      const delBtn = document.createElement('button');
      delBtn.type = 'button';
      delBtn.className = 'btn btn-danger btn-sm';
      delBtn.textContent = '删除';
      delBtn.disabled = mutateDisabled;
      delBtn.addEventListener('click', () => removeRow(row));
      actions.appendChild(editBtn);
      actions.appendChild(delBtn);
      tbody.appendChild(tr);
    });
  }

  function openCreate() {
    if (state.view === 'raw') {
      alert('请先切回业务视图再新增');
      return;
    }
    $('dialogTitle').textContent = '新增人员';
    $('editId').value = '';
    $('formName').value = '演示用户';
    $('formPhone').value = '13800138000';
    $('formIdCard').value = '110101199001011234';
    $('formAge').value = '28';
    $('formError').textContent = '';
    $('personDialog').showModal();
  }

  function openEdit(row) {
    if (state.view === 'raw') {
      alert('请先切回业务视图再编辑');
      return;
    }
    $('dialogTitle').textContent = '编辑人员 #' + row.id;
    $('editId').value = row.id;
    $('formName').value = row.name || '';
    $('formPhone').value = row.phone || '';
    $('formIdCard').value = row.idCard || '';
    $('formAge').value = row.age != null ? row.age : '';
    $('formError').textContent = '';
    $('personDialog').showModal();
  }

  async function removeRow(row) {
    if (state.view === 'raw') {
      alert('请先切回业务视图再删除');
      return;
    }
    if (!confirm('确认删除人员 #' + row.id + '？')) return;
    const res = await api('/api/person/delete.json', {
      method: 'POST',
      body: JSON.stringify({ datasourceId: currentDs(), id: row.id }),
    });
    if (!res.body.success) {
      alert(res.body.message || '删除失败');
      return;
    }
    await loadList();
  }

  async function savePerson(event) {
    event.preventDefault();
    const id = $('editId').value;
    const payload = {
      datasourceId: currentDs(),
      name: $('formName').value.trim(),
      phone: $('formPhone').value.trim(),
      idCard: $('formIdCard').value.trim(),
      age: $('formAge').value === '' ? null : Number($('formAge').value),
    };
    if (id) payload.id = Number(id);
    const path = id ? '/api/person/update.json' : '/api/person/create.json';
    const res = await api(path, { method: 'POST', body: JSON.stringify(payload) });
    if (!res.body.success) {
      $('formError').textContent = res.body.message || '保存失败';
      return;
    }
    $('personDialog').close();
    await loadList();
  }

  function setTab(tab) {
    state.tab = tab;
    document.querySelectorAll('.main-tab').forEach((btn) => {
      const active = btn.getAttribute('data-tab') === tab;
      btn.classList.toggle('active', active);
      btn.setAttribute('aria-selected', active ? 'true' : 'false');
    });
    $('personPanel').hidden = tab !== 'person';
    $('scenariosPanel').hidden = tab !== 'scenarios';
    $('pageTitle').textContent = tab === 'scenarios' ? '复杂查询' : '人员维护';
  }

  function renderSeedTable(hostId, rows) {
    const host = $(hostId);
    if (!host) return;
    if (!rows || !rows.length) {
      host.innerHTML = '<p class="empty">暂无数据</p>';
      return;
    }
    const cols = Object.keys(rows[0]);
    let html = '<table><thead><tr>' + cols.map((c) => '<th>' + escapeHtml(c) + '</th>').join('')
      + '</tr></thead><tbody>';
    rows.forEach((row) => {
      html += '<tr>' + cols.map((c) => '<td>' + escapeHtml(row[c]) + '</td>').join('') + '</tr>';
    });
    html += '</tbody></table>';
    host.innerHTML = html;
  }

  function escapeHtml(value) {
    if (value == null) return '';
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  async function loadSeedPreview() {
    const note = $('seedEncryptNote');
    const qs = currentDs() ? ('?datasourceId=' + encodeURIComponent(currentDs())) : '';
    const res = await api('/api/scenarios/seed-preview.json' + qs);
    if (!res.body.success) {
      note.textContent = res.body.message || '预览加载失败';
      renderSeedTable('userPreviewTable', []);
      renderSeedTable('ordersPreviewTable', []);
      return;
    }
    const data = res.body.data || {};
    note.textContent = data.encryptNote || '';
    renderSeedTable('userPreviewTable', data.userRows || []);
    renderSeedTable('ordersPreviewTable', data.ordersRows || []);
  }

  async function loadScenarios() {
    const res = await api('/api/scenarios.json');
    const list = (res.body.data || []);
    state.scenarios = list;
    const ul = $('scenarioList');
    ul.innerHTML = '';
    const hint = $('scenarioCatalogHint');
    if (!list.length) {
      hint.textContent = '暂无场景';
      return;
    }
    hint.textContent = res.body.message || '';
    list.forEach((item) => {
      const li = document.createElement('li');
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'scenario-item' + (item.available ? '' : ' unavailable');
      btn.textContent = item.title || item.id;
      btn.title = item.available ? (item.description || '') : (item.unavailableReason || '不可用');
      btn.addEventListener('click', () => selectScenario(item.id));
      li.appendChild(btn);
      ul.appendChild(li);
    });
    if (!state.selectedScenarioId && list.length) {
      selectScenario(list[0].id);
    } else if (state.selectedScenarioId) {
      selectScenario(state.selectedScenarioId);
    }
  }

  function currentScenario() {
    return state.scenarios.find((s) => s.id === state.selectedScenarioId);
  }

  function loadExampleSql(kind) {
    const item = currentScenario();
    if (!item) return;
    const sql = kind === 'cipher' ? item.exampleSqlCipher : item.exampleSqlPlain;
    if (sql) $('scenarioSqlEditor').value = sql;
    if (kind === 'cipher') $('useSkipCheckbox').checked = true;
  }

  function selectScenario(id) {
    state.selectedScenarioId = id;
    const item = state.scenarios.find((s) => s.id === id);
    document.querySelectorAll('.scenario-item').forEach((btn) => {
      btn.classList.toggle('active', btn.textContent === (item && (item.title || item.id)));
    });
    if (!item) return;
    $('scenarioTitle').textContent = item.title || item.id;
    $('scenarioDesc').textContent = item.available
      ? (item.description || '')
      : (item.unavailableReason || '场景不可用');
    const sampleHint = $('scenarioSampleHint');
    if (item.sampleHint) {
      sampleHint.hidden = false;
      sampleHint.textContent = item.sampleHint;
    } else {
      sampleHint.hidden = true;
      sampleHint.textContent = '';
    }
    const paramsHost = $('scenarioParams');
    paramsHost.innerHTML = '';
    const samples = item.sampleParams || {};
    (item.params || []).forEach((p) => {
      const wrap = document.createElement('div');
      wrap.className = 'form-group';
      const label = document.createElement('label');
      label.textContent = p.label || p.name;
      label.setAttribute('for', 'scenario-param-' + p.name);
      const input = document.createElement('input');
      input.id = 'scenario-param-' + p.name;
      input.name = p.name;
      input.placeholder = p.placeholder || '';
      input.required = !!p.required;
      if (samples[p.name] != null && samples[p.name] !== '') {
        input.value = String(samples[p.name]);
      }
      wrap.appendChild(label);
      wrap.appendChild(input);
      paramsHost.appendChild(wrap);
    });
    $('runScenarioBtn').disabled = !item.available;
    $('scenarioRunMessage').hidden = true;
    if (item.exampleSqlPlain) {
      $('scenarioSqlEditor').value = item.exampleSqlPlain;
    }
  }

  async function runScenario(event) {
    event.preventDefault();
    if (!state.selectedScenarioId) return;
    const params = {};
    $('scenarioParams').querySelectorAll('input').forEach((input) => {
      const v = input.value.trim();
      if (v) params[input.name] = v;
    });
    const res = await api('/api/scenarios/run.json', {
      method: 'POST',
      body: JSON.stringify({
        scenarioId: state.selectedScenarioId,
        datasourceId: currentDs(),
        params: params,
      }),
    });
    const msg = $('scenarioRunMessage');
    if (!res.body.success) {
      msg.hidden = false;
      msg.textContent = res.body.message || '执行失败';
      return;
    }
    msg.hidden = true;
    const data = res.body.data || {};
    $('plainRowsOut').textContent = JSON.stringify(data.plainRows || [], null, 2);
    $('cipherRowsOut').textContent = JSON.stringify(data.cipherRows || [], null, 2);
    $('sqlMetaOut').textContent = JSON.stringify(data.sqlMeta || {}, null, 2);
    if (data.sqlMeta && data.sqlMeta.sql) {
      $('scenarioSqlEditor').value = String(data.sqlMeta.sql);
    }
  }

  async function runSql() {
    const sql = ($('scenarioSqlEditor').value || '').trim();
    const msg = $('scenarioRunMessage');
    if (!sql) {
      msg.hidden = false;
      msg.textContent = '请先填写 SELECT SQL';
      return;
    }
    const res = await api('/api/scenarios/sql-run.json', {
      method: 'POST',
      body: JSON.stringify({
        sql: sql,
        useSkip: !!$('useSkipCheckbox').checked,
        datasourceId: currentDs(),
      }),
    });
    if (!res.body.success) {
      msg.hidden = false;
      msg.textContent = res.body.message || 'SQL 执行失败';
      return;
    }
    msg.hidden = true;
    const data = res.body.data || {};
    const rows = data.rows || [];
    const sqlMeta = data.sqlMeta || {};
    const skipped = !!sqlMeta.skipped;
    if (skipped) {
      $('cipherRowsOut').textContent = JSON.stringify(rows, null, 2);
      $('plainRowsOut').textContent = JSON.stringify({
        note: '本次为 SECURT_SKIP 原值，未做结果解密',
        rows: rows,
      }, null, 2);
    } else {
      $('plainRowsOut').textContent = JSON.stringify(rows, null, 2);
      $('cipherRowsOut').textContent = '（未请求 SECURT_SKIP 旁路）';
    }
    $('sqlMetaOut').textContent = JSON.stringify(sqlMeta, null, 2);
  }

  async function boot() {
    if (!(await ensureAuth())) return;
    await loadDatasources();
    await loadPreflight();
    setView('business');
    await loadList();
    await loadScenarios();
    setTab('person');
  }

  $('loginForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    $('loginError').textContent = '';
    const res = await api('/api/login.json', {
      method: 'POST',
      body: JSON.stringify({
        username: $('username').value,
        password: $('password').value,
      }),
    });
    if (!res.body.success) {
      $('loginError').textContent = res.body.message || '登录失败';
      return;
    }
    await boot();
  });

  $('logoutBtn').addEventListener('click', async () => {
    await api('/api/logout.json', { method: 'POST', body: '{}' });
    location.reload();
  });

  $('datasourceSelect').addEventListener('change', async () => {
    state.datasourceId = currentDs();
    await loadPreflight();
    if (state.tab === 'person') await loadList();
    else {
      await loadSeedPreview();
      await loadScenarios();
    }
  });

  document.querySelectorAll('.view-btn').forEach((btn) => {
    btn.addEventListener('click', async () => {
      setView(btn.getAttribute('data-view'));
      await loadList();
    });
  });

  document.querySelectorAll('.main-tab').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const tab = btn.getAttribute('data-tab');
      setTab(tab);
      if (tab === 'scenarios') {
        await loadSeedPreview();
        await loadScenarios();
      } else await loadList();
    });
  });

  $('createBtn').addEventListener('click', openCreate);
  $('filterForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    await loadList();
  });
  $('resetFilterBtn').addEventListener('click', async () => {
    $('filterName').value = '';
    $('filterPhone').value = '';
    $('filterIdCard').value = '';
    await loadList();
  });
  $('personForm').addEventListener('submit', savePerson);
  $('cancelDialogBtn').addEventListener('click', () => $('personDialog').close());
  $('scenarioForm').addEventListener('submit', runScenario);
  $('refreshSeedPreviewBtn').addEventListener('click', () => loadSeedPreview());
  $('loadPlainSqlBtn').addEventListener('click', () => loadExampleSql('plain'));
  $('loadCipherSqlBtn').addEventListener('click', () => loadExampleSql('cipher'));
  $('runSqlBtn').addEventListener('click', () => runSql());

  boot().catch((err) => {
    console.error(err);
    $('preflightTitle').textContent = '初始化失败';
    $('preflightSummary').textContent = String(err);
  });
})();
