const API_BASE = localStorage.getItem('drop_api_base') || 'http://localhost:8080';
const demoProducts = [
  {id: 1, brand: 'Hermès', name: 'Birkin 25 · Togo', category: 'Handbag', priceCents: 8600000, image: 'https://images.unsplash.com/photo-1584917865442-de89df76afd3?auto=format&fit=crop&w=700&q=85', badge: 'Live drop'},
  {id: 2, brand: 'The Row', name: 'Soft Margaux 12', category: 'Handbag', priceCents: 4200000, image: 'https://images.unsplash.com/photo-1594223274512-ad4803739b7c?auto=format&fit=crop&w=700&q=85', badge: 'Coming soon'},
  {id: 3, brand: 'Loro Piana', name: 'André Camp Moc', category: 'Shoes', priceCents: 1280000, image: 'https://images.unsplash.com/photo-1549298916-b41d501d3772?auto=format&fit=crop&w=700&q=85', badge: 'The edit'},
  {id: 4, brand: 'Bottega Veneta', name: 'Intrecciato Cassette', category: 'Accessories', priceCents: 2500000, image: 'https://images.unsplash.com/photo-1594223274512-ad4803739b7c?auto=format&fit=crop&w=700&q=85', badge: 'The edit'}
];
const categoryLabels = {Handbag: '手袋', Shoes: '鞋履', Accessories: '配饰', Object: '作品'};
const badgeLabels = {'Live drop': '当前发售', 'Coming soon': '即将发售', 'The edit': '精选'};
const sourceLabels = {PRODUCT: '商品资料', REVIEW: '已验证评价', PRODUCT_REVIEW: '已验证评价', product: '商品资料', review: '已验证评价'};
const state = {products: [], drops: [], demo: false, authMode: 'login', user: JSON.parse(localStorage.getItem('drop_user') || 'null'), countdown: 18 * 60 + 42, filterMode: 'all'};
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const labelFor = (labels, value, fallback = value) => labels[value] || fallback;

async function api(path, options = {}) {
  const headers = {'Content-Type': 'application/json', ...(options.headers || {})};
  const token = localStorage.getItem('drop_token');
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(`${API_BASE}${path}`, {...options, headers});
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const body = await response.json();
  if (body.code !== 0) throw new Error(body.message || 'Request failed');
  return body.data;
}

function money(cents) {
  if (cents == null) return '价格面议';
  return `¥ ${(Number(cents) / 100).toLocaleString('en-US')}`;
}

function renderProducts(products) {
  const grid = $('[data-product-grid]');
  $('[data-product-count]').textContent = `${String(products.length).padStart(2, '0')} 件`;
  grid.innerHTML = products.map(product => `<article class="product-card">
    <div class="product-image"><img src="${product.image || demoProducts.find(item => item.id === product.id)?.image || demoProducts[0].image}" alt="${product.brand || ''} ${product.name || '作品'}" loading="lazy"><span class="product-badge">${labelFor(badgeLabels, product.badge, '精选')}</span></div>
    <div class="product-info"><p class="product-meta">${product.brand || 'Maison de Port'} / ${labelFor(categoryLabels, product.category, '作品')}</p><h3>${product.name || '未命名作品'}</h3><div class="product-details"><span>已完成鉴定</span><span class="product-price">${money(product.priceCents)}</span></div></div>
  </article>`).join('');
}

function filteredProducts() {
  if (state.filterMode === 'all') return state.products;
  return state.products.filter(product => (product.category || '').toLowerCase() === state.filterMode.toLowerCase());
}

function toggleSearch() {
  let input = $('.catalog-search');
  if (!input) {
    input = document.createElement('input');
    input.className = 'catalog-search';
    input.hidden = true;
    input.placeholder = '搜索精选商品';
    input.setAttribute('aria-label', '搜索精选商品');
    $('.section-header').after(input);
    input.addEventListener('input', () => {
      const query = input.value.toLowerCase().trim();
      renderProducts(filteredProducts().filter(product => `${product.brand} ${product.name} ${product.category}`.toLowerCase().includes(query)));
    });
  }
  input.hidden = !input.hidden;
  if (!input.hidden) input.focus();
}

function cycleFilter() {
  const modes = ['all', 'Handbag', 'Shoes'];
  state.filterMode = modes[(modes.indexOf(state.filterMode) + 1) % modes.length];
  const button = $('.filter-button');
  const filterLabel = state.filterMode === 'all' ? '筛选' : `筛选：${labelFor(categoryLabels, state.filterMode)}`;
  button.innerHTML = `${filterLabel} <i data-lucide="sliders-horizontal"></i>`;
  if (window.lucide) lucide.createIcons();
  renderProducts(filteredProducts());
}

function formatCountdown(total) {
  const hours = Math.floor(total / 3600).toString().padStart(2, '0');
  const minutes = Math.floor((total % 3600) / 60).toString().padStart(2, '0');
  const seconds = (total % 60).toString().padStart(2, '0');
  return `${hours} : ${minutes} : ${seconds}`;
}

function updateCountdown() {
  const target = $('[data-countdown]');
  if (!target) return;
  target.textContent = formatCountdown(state.countdown);
  const progress = $('[data-countdown-progress]');
  if (progress) progress.style.width = `${Math.max(7, Math.min(93, state.countdown / (18 * 60 + 42) * 100))}%`;
  if (state.countdown > 0) state.countdown -= 1;
}

function renderLiveDrop(drop, info) {
  const product = state.products.find(item => item.id === drop?.productId) || demoProducts[0];
  const liveName = (product.name || 'Birkin 25').replace(/\bleather\b/ig, '皮革');
  $('[data-live-name]').textContent = liveName.toLowerCase().includes('togo')
    ? (liveName.includes('皮革') ? liveName : `${liveName} 皮革`)
    : `${liveName} · Togo 皮革`;
  $('[data-live-category]').textContent = `${product.brand || 'Hermès'} / ${labelFor(categoryLabels, product.category, '手袋')}`;
  $('[data-live-price]').textContent = money(drop?.priceCents || product.priceCents);
  $('[data-remaining]').textContent = info?.remaining ?? drop?.stock ?? '24';
  if (info?.status && info.status !== 'OPEN') $('.live-indicator').innerHTML = `<i></i>${info.status === 'SCHEDULED' ? '尚未开始' : '本场发售已结束'}`;
}

async function loadProducts() {
  try {
    const page = await api('/api/product/products?page=1&size=20');
    state.products = (page.records || []).map(product => ({...product, image: demoProducts.find(item => item.id === product.id)?.image}));
    if (!state.products.length) throw new Error('empty');
  } catch (error) {
    state.demo = true;
    state.products = demoProducts;
    $('[data-demo-note]').hidden = false;
  }
  renderProducts(state.products);
}

async function loadDrops() {
  try {
    state.drops = await api('/api/product/drops');
    const live = state.drops.find(drop => drop.status === 'OPEN') || state.drops[0];
    if (live) {
      let info;
      try { info = await api(`/api/flashsale/drops/${live.id}/info`); } catch (_) { /* service may be offline */ }
      renderLiveDrop(live, info);
    } else renderLiveDrop(null, null);
  } catch (_) {
    state.demo = true;
    renderLiveDrop(null, {remaining: 24, status: 'OPEN'});
    $('[data-demo-note]').hidden = false;
  }
}

function toast(message) {
  const element = $('[data-toast]');
  element.textContent = message;
  element.classList.add('is-visible');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => element.classList.remove('is-visible'), 3200);
}

function updateAccount() {
  const label = $('[data-account-label]');
  if (state.user) label.textContent = state.user.username || '我的账户';
}

function openModal() { $('[data-modal]').hidden = false; document.body.style.overflow = 'hidden'; setTimeout(() => $('[data-auth-form] input')?.focus(), 50); }
function closeModal() { $('[data-modal]').hidden = true; document.body.style.overflow = ''; }

function setAuthMode(mode) {
  state.authMode = mode;
  $$('[data-auth-tab]').forEach(tab => tab.classList.toggle('is-active', tab.dataset.authTab === mode));
  $('[data-password-label]').firstElementChild.textContent = '密码';
  $('[data-auth-submit]').innerHTML = `${mode === 'register' ? '创建账户' : '登录'} <i data-lucide="arrow-up-right"></i>`;
  if (window.lucide) lucide.createIcons();
}

async function submitAuth(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const feedback = $('[data-form-feedback]');
  const payload = Object.fromEntries(new FormData(form));
  feedback.textContent = '';
  try {
    const data = await api(`/api/user/auth/${state.authMode}`, {method: 'POST', body: JSON.stringify(payload)});
    localStorage.setItem('drop_token', data.token);
    localStorage.setItem('drop_user', JSON.stringify(data));
    state.user = data;
    updateAccount();
    closeModal();
    toast(`欢迎回来，${data.username || payload.username}。`);
  } catch (_) {
    if (state.demo) {
      state.user = {username: payload.username, userId: 1};
      localStorage.setItem('drop_user', JSON.stringify(state.user));
      updateAccount(); closeModal(); toast('演示账户已准备好。连接用户服务后即可进行真实登录。');
    } else feedback.textContent = '登录失败，请检查信息后重试。';
  }
}

async function buy() {
  if (!state.user || !localStorage.getItem('drop_token')) { openModal(); toast('请先登录，再申请购买。'); return; }
  const drop = state.drops.find(item => item.status === 'OPEN') || state.drops[0];
  if (!drop) { toast('当前没有可申请的发售。'); return; }
  try {
    const result = await api(`/api/flashsale/drops/${drop.id}/buy`, {method: 'POST'});
    if (result.code === 0 || result.code === -2) toast(`申请已锁定 · ${result.orderNo}`);
    else if (result.code === -1) toast('本场发售已售罄。');
    else if (result.code === -3) toast('本场发售尚未开始。');
  } catch (_) { toast(state.demo ? '演示申请已记录。连接平台服务后即可锁定库存。' : '申请未能完成，请稍后重试。'); }
}

async function askQuestion(question) {
  const conversation = $('[data-conversation]');
  conversation.insertAdjacentHTML('beforeend', `<div class="user-message"><p>${escapeHtml(question)}</p></div>`);
  const pending = document.createElement('div'); pending.className = 'assistant-message pending-message'; pending.innerHTML = '<span class="message-mark">MP</span><p>正在查找精选资料……</p>'; conversation.appendChild(pending);
  conversation.scrollTop = conversation.scrollHeight;
  try {
    const data = await api('/api/qa/ask', {method: 'POST', body: JSON.stringify({question, topK: 5})});
    pending.remove();
    const refs = (data.sources || []).slice(0, 3).map((source, index) => escapeHtml(`[${index + 1}] ${labelFor(sourceLabels, source.sourceType, '资料')} · ${source.content}`)).join('<br>');
    conversation.insertAdjacentHTML('beforeend', `<div class="assistant-message"><span class="message-mark">MP</span><div><p>${escapeHtml(data.answer || '暂时没有找到答案。')}</p>${refs ? `<div class="source-line">${refs}</div>` : ''}</div></div>`);
  } catch (_) {
    pending.remove();
    const fallback = question.toLowerCase().includes('review') || question.includes('评价') ? '已验证买家提到包装细致、皮革手感出色，尺寸也符合标准。' : '这些精选作品适合长期使用。您可以继续询问护理、尺寸或买家评价。';
    conversation.insertAdjacentHTML('beforeend', `<div class="assistant-message"><span class="message-mark">MP</span><p>${fallback}</p></div>`);
  }
  conversation.scrollTop = conversation.scrollHeight;
}

function escapeHtml(value) { const div = document.createElement('div'); div.textContent = value; return div.innerHTML; }

function bindEvents() {
  document.addEventListener('click', event => {
    const action = event.target.closest('[data-action]')?.dataset.action;
    if (action === 'open-account') openModal();
    if (action === 'close-modal') closeModal();
    if (action === 'buy') buy();
    if (action === 'toggle-menu') document.body.classList.toggle('menu-open');
    if (action === 'toggle-search') toggleSearch();
    if (action === 'toggle-filter') cycleFilter();
    if (event.target.closest('[data-auth-tab]')) setAuthMode(event.target.closest('[data-auth-tab]').dataset.authTab);
    const suggestion = event.target.closest('[data-question]');
    if (suggestion) { $('#question').value = suggestion.dataset.question; askQuestion(suggestion.dataset.question); }
  });
  $('[data-auth-form]').addEventListener('submit', submitAuth);
  $('[data-question-form]').addEventListener('submit', event => { event.preventDefault(); const input = $('#question'); if (input.value.trim()) { const question = input.value.trim(); input.value = ''; askQuestion(question); } });
  $('[data-modal]').addEventListener('click', event => { if (event.target === $('[data-modal]')) closeModal(); });
}

document.addEventListener('DOMContentLoaded', async () => {
  bindEvents();
  if (window.lucide) lucide.createIcons();
  updateAccount();
  await Promise.all([loadProducts(), loadDrops()]);
  updateCountdown();
  setInterval(updateCountdown, 1000);
});
