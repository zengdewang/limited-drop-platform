const API_BASE = localStorage.getItem('drop_api_base') || 'http://localhost:8080';
const PLACEHOLDER_IMAGE = 'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?auto=format&fit=crop&w=900&q=80';
const demoProducts = [
  {id: 1, brand: 'Hermès', name: 'Birkin 25 · Togo', category: 'Handbag', priceCents: 8600000, imageUrl: 'https://images.unsplash.com/photo-1584917865442-de89df76afd3?auto=format&fit=crop&w=700&q=85', badge: 'Live drop'},
  {id: 2, brand: 'The Row', name: 'Soft Margaux 12', category: 'Handbag', priceCents: 4200000, imageUrl: 'https://images.unsplash.com/photo-1594223274512-ad4803739b7c?auto=format&fit=crop&w=700&q=85', badge: 'Coming soon'},
  {id: 3, brand: 'Loro Piana', name: 'André Camp Moc', category: 'Shoes', priceCents: 1280000, imageUrl: 'https://images.unsplash.com/photo-1549298916-b41d501d3772?auto=format&fit=crop&w=700&q=85', badge: 'The edit'},
  {id: 4, brand: 'Bottega Veneta', name: 'Intrecciato Cassette', category: 'Accessories', priceCents: 2500000, imageUrl: 'https://images.unsplash.com/photo-1566150905458-1bf1fc113f0d?auto=format&fit=crop&w=700&q=85', badge: 'The edit'}
];
const categoryLabels = {Handbag: '手袋', Shoes: '鞋履', Accessories: '配饰', Object: '作品'};
const badgeLabels = {'Live drop': '当前发售', 'Coming soon': '即将发售', 'The edit': '精选'};
const sourceLabels = {PRODUCT: '商品资料', REVIEW: '已验证评价', PRODUCT_REVIEW: '已验证评价', product: '商品资料', review: '已验证评价'};
const reviewStatusLabels = {PENDING: '审核中', APPROVED: '已公开', REJECTED: '未通过'};
const state = {products: [], drops: [], orders: [], reviewStatuses: new Map(), liveDrop: null, liveInfo: null, demo: false, authMode: 'login', user: JSON.parse(localStorage.getItem('drop_user') || 'null'), countdown: 18 * 60 + 42, filterMode: 'all', opsKey: '', selectedRating: 0};
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const labelFor = (labels, value, fallback = value) => labels[value] || fallback;

class ApiError extends Error {
  constructor(message, statusCode) {
    super(message);
    this.name = 'ApiError';
    this.statusCode = statusCode;
  }
}

async function api(path, options = {}) {
  const {auth = true, ...fetchOptions} = options;
  const headers = {'Content-Type': 'application/json', ...(fetchOptions.headers || {})};
  const token = auth ? localStorage.getItem('drop_token') : null;
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(`${API_BASE}${path}`, {...fetchOptions, headers});
  const body = await response.json().catch(() => null);
  if (response.status === 401 && auth) clearSession();
  if (!response.ok) throw new ApiError(body?.message || `HTTP ${response.status}`, response.status);
  if (!body || body.code !== 0) throw new ApiError(body?.message || 'Request failed', response.status);
  return body.data;
}

function money(cents) {
  if (cents == null) return '价格面议';
  return `¥ ${(Number(cents) / 100).toLocaleString('en-US')}`;
}

const orderStatusLabels = {PENDING_PAYMENT: '待支付', PAID: '已支付 · 已收货', EXPIRED: '已过期'};

function clearSession() {
  localStorage.removeItem('drop_token');
  localStorage.removeItem('drop_user');
  state.user = null;
  state.orders = [];
  state.reviewStatuses = new Map();
  updateAccount();
}

function formatDate(value) {
  if (!value) return '时间未知';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '时间未知' : date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  });
}

function renderOrders() {
  const list = $('[data-order-list]');
  const count = $('[data-order-count]');
  if (!list || !count) return;
  count.textContent = `${String(state.orders.length).padStart(2, '0')} 笔`;
  if (!state.orders.length) {
    list.innerHTML = '<div class="empty-state">暂时没有订单记录。</div>';
    return;
  }
  list.innerHTML = state.orders.map(order => {
    const product = state.products.find(item => item.id === order.productId);
    const productName = product?.name || `商品 #${order.productId ?? '-'}`;
    const productBrand = product?.brand || 'Maison de Port';
    const productImage = product?.imageUrl || PLACEHOLDER_IMAGE;
    const status = orderStatusLabels[order.status] || order.status || '未知状态';
    const payAction = order.status === 'PENDING_PAYMENT'
      ? `<button class="primary-button compact order-pay" data-action="pay-order" data-order-no="${escapeHtml(order.orderNo)}"><i data-lucide="credit-card"></i>模拟支付</button>`
      : '';
    const reviewState = state.reviewStatuses.get(order.orderNo);
    let reviewAction = '';
    if (order.status === 'PAID') {
      if (reviewState?.reviewed) {
        reviewAction = `<span class="review-state">已评价 · ${labelFor(reviewStatusLabels, reviewState.reviewStatus, '处理中')}</span>`;
      } else if (reviewState?.eligible) {
        reviewAction = `<button class="secondary-button compact" data-action="review-order" data-order-no="${escapeHtml(order.orderNo)}"><i data-lucide="star"></i>评价</button>`;
      } else {
        reviewAction = '<button class="secondary-button compact" disabled><i data-lucide="loader-circle"></i>资格同步中</button>';
      }
    }
    return `<article class="order-item"><div class="order-main"><img class="order-image" src="${escapeHtml(productImage)}" alt="${escapeHtml(productBrand)} ${escapeHtml(productName)}" data-image-fallback><div class="order-copy"><p class="order-kicker">订单 ${escapeHtml(order.orderNo || '未知')}</p><h3>${escapeHtml(productName)}</h3><p class="order-subline">${escapeHtml(productBrand)} · Drop #${escapeHtml(order.dropId ?? '-')} · ${formatDate(order.createdAt)}</p></div><div class="order-meta"><strong>${money(order.amountCents)}</strong><span class="order-status order-status-${String(order.status || '').toLowerCase()}">${escapeHtml(status)}</span>${payAction}${reviewAction}</div></div></article>`;
  }).join('');
  if (window.lucide) lucide.createIcons();
}

async function loadOrders({silent = false} = {}) {
  const list = $('[data-order-list]');
  const count = $('[data-order-count]');
  if (!list || !count) return;
  if (!state.user || !localStorage.getItem('drop_token')) {
    state.orders = [];
    count.textContent = '登录后查看';
    list.innerHTML = '<div class="empty-state">登录后查看订单记录。</div>';
    return;
  }
  if (!silent) list.innerHTML = '<div class="loading">正在加载订单……</div>';
  try {
    const page = await api('/api/orders/my?page=1&size=20');
    state.orders = page.records || [];
    await loadReviewStatuses();
    renderOrders();
  } catch (error) {
    state.orders = [];
    count.textContent = '加载失败';
    list.innerHTML = `<div class="empty-state">${error?.statusCode === 401 ? '登录已过期，请重新登录。' : escapeHtml(error?.message || '订单暂时无法加载，请稍后重试。')}</div>`;
  }
}

async function loadReviewStatuses() {
  const paidOrders = state.orders.filter(order => order.status === 'PAID' && order.orderNo);
  if (!paidOrders.length) {
    state.reviewStatuses = new Map();
    return;
  }
  const query = paidOrders.map(order => `orderNos=${encodeURIComponent(order.orderNo)}`).join('&');
  try {
    const statuses = await api(`/api/product/reviews/my?${query}`);
    state.reviewStatuses = new Map((statuses || []).map(item => [item.orderNo, item]));
  } catch (error) {
    if (error?.statusCode === 401) throw error;
  }
}

function openOrders() {
  document.body.classList.remove('menu-open');
  if (!state.user || !localStorage.getItem('drop_token')) {
    openModal();
    toast('请先登录，再查看订单。');
    return;
  }
  document.querySelector('#orders')?.scrollIntoView({behavior: 'smooth'});
  loadOrders();
}

async function payOrder(orderNo) {
  if (!orderNo) return;
  try {
    await api(`/api/orders/${encodeURIComponent(orderNo)}/pay`, {method: 'POST'});
    toast('支付成功，订单已视为收货。');
  } catch (error) {
    toast(error?.message || '支付未完成，请稍后重试。');
    await loadOrders({silent: true});
    return;
  }
  await loadOrders({silent: true});
  pollReviewEligibility(orderNo);
}

function renderProducts(products) {
  const grid = $('[data-product-grid]');
  $('[data-product-count]').textContent = `${String(products.length).padStart(2, '0')} 件`;
  grid.innerHTML = products.map(product => `<article class="product-card">
    <div class="product-image"><img src="${escapeHtml(product.imageUrl || PLACEHOLDER_IMAGE)}" alt="${escapeHtml(product.brand || '')} ${escapeHtml(product.name || '作品')}" loading="lazy" data-image-fallback><span class="product-badge">${labelFor(badgeLabels, product.badge, '精选')}</span></div>
    <div class="product-info"><p class="product-meta">${escapeHtml(product.brand || 'Maison de Port')} / ${escapeHtml(labelFor(categoryLabels, product.category, '作品'))}</p><h3>${escapeHtml(product.name || '未命名作品')}</h3><div class="product-details"><span>已完成鉴定</span><span class="product-price">${money(product.priceCents)}</span></div></div>
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
  const modes = ['all', 'Handbag', 'Shoes', 'Accessories', 'Object'];
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
  const liveName = (product.name || 'Birkin 25 · Togo').replace(/\bleather\b/ig, '皮革');
  $('[data-live-name]').textContent = liveName;
  $('[data-live-category]').textContent = `${product.brand || 'Hermès'} / ${labelFor(categoryLabels, product.category, '手袋')}`;
  $('[data-live-price]').textContent = money(drop?.priceCents || product.priceCents);
  $('[data-remaining]').textContent = info?.remaining ?? drop?.stock ?? '24';
  $('[data-live-stock]').textContent = `共 ${drop?.stock ?? info?.stock ?? 40} 件`;
  const liveImage = $('.live-thumb img');
  if (liveImage) {
    liveImage.src = product.imageUrl || PLACEHOLDER_IMAGE;
    liveImage.alt = `${product.brand || ''} ${product.name || '当前发售商品'}`.trim();
  }
  if (info?.status) {
    const label = info.status === 'OPEN' ? '正在接受申请' : info.status === 'SCHEDULED' ? '尚未开始' : '本场发售已结束';
    $('.live-indicator').innerHTML = `<i></i>${label}`;
  }
}

async function loadProducts() {
  try {
    const page = await api('/api/product/products?page=1&size=20');
    state.products = page.records || [];
    if (!state.products.length) throw new Error('empty');
  } catch (error) {
    state.demo = true;
    state.products = demoProducts;
    $('[data-demo-note]').hidden = false;
  }
  renderProducts(state.products);
  $('[data-hero-product-count]').textContent = String(state.products.length).padStart(2, '0');
  renderProductOptions();
  if (state.liveDrop) renderLiveDrop(state.liveDrop, state.liveInfo);
}

async function loadDrops() {
  try {
    state.drops = await api('/api/product/drops');
    const candidates = await Promise.all(state.drops.map(async drop => {
      try {
        return {drop, info: await api(`/api/flashsale/drops/${drop.id}/info`)};
      } catch (_) {
        return {drop, info: null};
      }
    }));
    state.drops = candidates.map(candidate => ({
      ...candidate.drop,
      runtimeStatus: candidate.info?.status || candidate.drop.status,
      remaining: candidate.info?.remaining
    }));
    const live = candidates.find(candidate => candidate.info?.status === 'OPEN')
      || candidates.find(candidate => candidate.info?.status === 'SCHEDULED')
      || candidates[0];
    state.liveDrop = live?.drop || null;
    state.liveInfo = live?.info || null;
    renderLiveDrop(state.liveDrop, state.liveInfo);
    renderDropAdmin();
  } catch (_) {
    state.demo = true;
    state.liveDrop = null;
    state.liveInfo = null;
    renderLiveDrop(null, {remaining: 24, status: 'OPEN'});
    $('[data-demo-note]').hidden = false;
    renderDropAdmin();
  }
}

const wait = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));

async function pollOrder(orderNo) {
  for (let attempt = 0; attempt < 12; attempt += 1) {
    try {
      await api(`/api/orders/${encodeURIComponent(orderNo)}`);
      await loadOrders({silent: true});
      toast('订单已生成，可在“我的订单”中查看。');
      return;
    } catch (error) {
      if (error?.statusCode !== 404) return;
      await wait(500);
    }
  }
  await loadOrders({silent: true});
  toast('订单仍在生成，请稍后刷新订单。');
}

async function pollReviewEligibility(orderNo) {
  for (let attempt = 0; attempt < 10; attempt += 1) {
    await loadReviewStatuses();
    renderOrders();
    if (state.reviewStatuses.get(orderNo)?.eligible) return;
    await wait(600);
  }
}

function openReview(orderNo) {
  const order = state.orders.find(item => item.orderNo === orderNo);
  const reviewState = state.reviewStatuses.get(orderNo);
  if (!order || order.status !== 'PAID' || !reviewState?.eligible || reviewState.reviewed) return;
  const product = state.products.find(item => item.id === order.productId);
  const form = $('[data-review-form]');
  form.reset();
  form.elements.orderNo.value = orderNo;
  state.selectedRating = 0;
  updateRatingButtons();
  $('[data-review-product]').textContent = `${product?.brand || 'Maison de Port'} / ${product?.name || `商品 #${order.productId}`}`;
  $('[data-review-feedback]').textContent = '';
  $('[data-review-modal]').hidden = false;
  document.body.style.overflow = 'hidden';
}

function closeReview() {
  $('[data-review-modal]').hidden = true;
  document.body.style.overflow = '';
}

function updateRatingButtons() {
  $$('[data-rating]').forEach(button => {
    const selected = Number(button.dataset.rating) <= state.selectedRating;
    button.classList.toggle('is-selected', selected);
    button.setAttribute('aria-pressed', String(selected));
  });
}

async function submitReview(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const feedback = $('[data-review-feedback]');
  if (!state.selectedRating) {
    feedback.textContent = '请选择 1 到 5 星评分。';
    return;
  }
  const submit = form.querySelector('[type="submit"]');
  submit.disabled = true;
  try {
    await api('/api/product/reviews', {
      method: 'POST',
      body: JSON.stringify({
        orderNo: form.elements.orderNo.value,
        rating: state.selectedRating,
        content: form.elements.content.value.trim()
      })
    });
    await loadReviewStatuses();
    renderOrders();
    closeReview();
    toast('评价已提交，状态可在订单中查看。');
  } catch (error) {
    feedback.textContent = error?.message || '评价提交失败，请稍后重试。';
  } finally {
    submit.disabled = false;
  }
}

function openManagement() {
  $('[data-management]').hidden = false;
  document.body.style.overflow = 'hidden';
  $('[data-management-content]').hidden = !state.opsKey;
  $('[data-ops-form]').hidden = Boolean(state.opsKey);
  renderProductOptions();
  renderDropAdmin();
}

function closeManagement() {
  $('[data-management]').hidden = true;
  document.body.style.overflow = '';
}

async function unlockManagement(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const feedback = $('[data-ops-feedback]');
  const opsKey = form.elements.opsKey.value.trim();
  feedback.textContent = '';
  try {
    await api('/api/product/reviews/pending', {headers: {'X-Ops-Key': opsKey}, auth: false});
    state.opsKey = opsKey;
    form.reset();
    form.hidden = true;
    $('[data-management-content]').hidden = false;
    renderProductOptions();
    renderDropAdmin();
  } catch (error) {
    feedback.textContent = error?.statusCode === 401 ? 'Ops Key 无效。' : (error?.message || '管理权限验证失败。');
  }
}

function renderProductOptions() {
  const select = $('[data-product-select]');
  if (!select) return;
  select.innerHTML = state.products.length
    ? state.products.map(product => `<option value="${product.id}">${escapeHtml(product.brand || '')} / ${escapeHtml(product.name || `商品 #${product.id}`)}</option>`).join('')
    : '<option value="">暂无商品</option>';
}

function renderDropAdmin() {
  const list = $('[data-drop-admin-list]');
  if (!list) return;
  if (!state.drops.length) {
    list.innerHTML = '<div class="empty-state">暂无发售记录。</div>';
    return;
  }
  list.innerHTML = state.drops.map(drop => {
    const product = state.products.find(item => item.id === drop.productId);
    const status = drop.runtimeStatus || drop.status || 'SCHEDULED';
    const statusLabel = {SCHEDULED: '待开售', OPEN: '进行中', ENDED: '已结束'}[status] || status;
    const action = status === 'OPEN'
      ? `<button class="secondary-button compact" data-action="control-drop" data-command="close" data-drop-id="${drop.id}"><i data-lucide="circle-stop"></i>结束</button>`
      : status === 'ENDED' ? '' : `<button class="primary-button compact" data-action="control-drop" data-command="open" data-drop-id="${drop.id}"><i data-lucide="circle-play"></i>开售</button>`;
    return `<article class="drop-admin-item"><div><p>${escapeHtml(product?.brand || drop.brand || '')}</p><h4>${escapeHtml(drop.name || product?.name || `Drop #${drop.id}`)}</h4><span>${formatDate(drop.startTime)} · 库存 ${drop.stock} · ${money(drop.priceCents)}</span></div><div class="drop-admin-actions"><strong class="drop-status drop-status-${status.toLowerCase()}">${escapeHtml(statusLabel)}</strong>${action}</div></article>`;
  }).join('');
  if (window.lucide) lucide.createIcons();
}

async function createProduct(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const feedback = $('[data-product-feedback]');
  const submit = form.querySelector('[type="submit"]');
  const payload = Object.fromEntries(new FormData(form));
  submit.disabled = true;
  feedback.textContent = '';
  try {
    await api('/api/product/products', {method: 'POST', headers: {'X-Ops-Key': state.opsKey}, body: JSON.stringify(payload), auth: false});
    form.reset();
    await loadProducts();
    toast('商品已创建并发布官方资料。');
  } catch (error) {
    feedback.textContent = error?.message || '商品创建失败。';
  } finally {
    submit.disabled = false;
  }
}

async function createDrop(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const feedback = $('[data-drop-feedback]');
  const submit = form.querySelector('[type="submit"]');
  const values = Object.fromEntries(new FormData(form));
  if (new Date(values.endTime) <= new Date(values.startTime)) {
    feedback.textContent = '结束时间必须晚于开始时间。';
    return;
  }
  const payload = {
    productId: Number(values.productId), name: values.name.trim(),
    startTime: values.startTime, endTime: values.endTime,
    stock: Number(values.stock), priceCents: Math.round(Number(values.priceYuan) * 100)
  };
  submit.disabled = true;
  feedback.textContent = '';
  try {
    await api('/api/product/drops', {method: 'POST', headers: {'X-Ops-Key': state.opsKey}, body: JSON.stringify(payload), auth: false});
    form.reset();
    await loadDrops();
    toast('发售已创建，等待同步后即可开售。');
  } catch (error) {
    feedback.textContent = error?.message || '发售创建失败。';
  } finally {
    submit.disabled = false;
  }
}

async function controlDrop(dropId, command) {
  try {
    await api(`/api/flashsale/drops/${encodeURIComponent(dropId)}/${command}`, {method: 'POST', headers: {'X-Ops-Key': state.opsKey}, auth: false});
    await loadDrops();
    toast(command === 'open' ? '发售已开售。' : '发售已结束。');
  } catch (error) {
    toast(error?.message || '发售状态更新失败，请确认数据已同步。');
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
  if (label) label.textContent = state.user ? (state.user.username || '我的账户') : '登录';
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
    loadOrders({silent: true});
  } catch (error) {
    if (state.demo && !error?.statusCode) {
      state.user = {username: payload.username, userId: 1};
      localStorage.setItem('drop_user', JSON.stringify(state.user));
      updateAccount(); closeModal(); toast('演示账户已准备好。连接用户服务后即可进行真实登录。');
    } else if (state.authMode === 'login' && error?.message === '用户名或密码错误') {
      feedback.textContent = '用户名或密码错误；首次使用请切换到“创建账户”。';
    } else {
      feedback.textContent = error?.message || `${state.authMode === 'register' ? '注册' : '登录'}失败，请检查信息后重试。`;
    }
  }
}

async function buy() {
  if (!state.user || !localStorage.getItem('drop_token')) { openModal(); toast('请先登录，再申请购买。'); return; }
  const drop = state.liveDrop || state.drops[0];
  if (!drop) { toast('当前没有可申请的发售。'); return; }
  try {
    const info = await api(`/api/flashsale/drops/${drop.id}/info`);
    state.liveInfo = info;
    if (info.status !== 'OPEN') {
      toast(info.status === 'ENDED' ? '本场发售已结束。' : '本场发售尚未开始。');
      renderLiveDrop(drop, info);
      return;
    }
    const result = await api(`/api/flashsale/drops/${drop.id}/buy`, {method: 'POST'});
    if (result.code === 0 || result.code === -2) {
      toast(`申请已锁定 · ${result.orderNo}`);
      pollOrder(result.orderNo);
    }
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
    if (action === 'open-orders') openOrders();
    if (action === 'refresh-orders') loadOrders();
    if (action === 'pay-order') payOrder(event.target.closest('[data-order-no]')?.dataset.orderNo);
    if (action === 'review-order') openReview(event.target.closest('[data-order-no]')?.dataset.orderNo);
    if (action === 'close-review') closeReview();
    if (action === 'open-management') openManagement();
    if (action === 'close-management') closeManagement();
    if (action === 'control-drop') {
      const target = event.target.closest('[data-drop-id]');
      controlDrop(target?.dataset.dropId, target?.dataset.command);
    }
    if (action === 'close-modal') closeModal();
    if (action === 'buy') buy();
    if (action === 'toggle-menu') document.body.classList.toggle('menu-open');
    if (action === 'toggle-search') toggleSearch();
    if (action === 'toggle-filter') cycleFilter();
    if (event.target.closest('[data-auth-tab]')) setAuthMode(event.target.closest('[data-auth-tab]').dataset.authTab);
    if (event.target.closest('[data-rating]')) {
      state.selectedRating = Number(event.target.closest('[data-rating]').dataset.rating);
      updateRatingButtons();
    }
    const suggestion = event.target.closest('[data-question]');
    if (suggestion) { $('#question').value = suggestion.dataset.question; askQuestion(suggestion.dataset.question); }
  });
  $('[data-auth-form]').addEventListener('submit', submitAuth);
  $('[data-review-form]').addEventListener('submit', submitReview);
  $('[data-ops-form]').addEventListener('submit', unlockManagement);
  $('[data-product-form]').addEventListener('submit', createProduct);
  $('[data-drop-form]').addEventListener('submit', createDrop);
  $('[data-question-form]').addEventListener('submit', event => { event.preventDefault(); const input = $('#question'); if (input.value.trim()) { const question = input.value.trim(); input.value = ''; askQuestion(question); } });
  $('[data-modal]').addEventListener('click', event => { if (event.target === $('[data-modal]')) closeModal(); });
  $('[data-review-modal]').addEventListener('click', event => { if (event.target === $('[data-review-modal]')) closeReview(); });
  document.addEventListener('keydown', event => {
    if (event.key !== 'Escape') return;
    if (!$('[data-review-modal]').hidden) closeReview();
    else if (!$('[data-management]').hidden) closeManagement();
    else if (!$('[data-modal]').hidden) closeModal();
  });
  document.addEventListener('error', event => {
    if (event.target.matches?.('[data-image-fallback]') && event.target.src !== PLACEHOLDER_IMAGE) {
      event.target.src = PLACEHOLDER_IMAGE;
    }
  }, true);
}

document.addEventListener('DOMContentLoaded', async () => {
  bindEvents();
  if (window.lucide) lucide.createIcons();
  updateAccount();
  await loadProducts();
  await loadDrops();
  loadOrders({silent: true});
  updateCountdown();
  setInterval(updateCountdown, 1000);
});
