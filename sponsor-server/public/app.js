let csrf = '', enabled = true, metricsRequest = 0;
const $ = id => document.getElementById(id);
const message = text => { $('message').textContent = text; };
async function api(path, body) {
  const response = await fetch(path, { credentials: 'same-origin', cache: 'no-store', method: body === undefined ? 'GET' : 'POST', headers: { 'Content-Type': 'application/json', 'X-Dashboard': '1', 'X-CSRF-Token': csrf }, body: body === undefined ? undefined : JSON.stringify(body) });
  const data = await response.json();
  if (!response.ok) { if (response.status === 401) showLogin(); throw Error(data.error || 'Request failed.'); }
  return data;
}
function showLogin() { $('login').hidden = false; $('dashboard').hidden = true; $('logout').hidden = true; csrf = ''; }
function showDashboard() { $('login').hidden = true; $('dashboard').hidden = false; $('logout').hidden = false; }
async function run(action) { try { await action(); } catch (error) { message(error.message); } }
$('login-form').onsubmit = event => { event.preventDefault(); run(async () => { const result = await api('/api/login', { password: $('password').value }); csrf = result.csrf; $('password').value = ''; await refresh(); message(''); }); };
$('logout').onclick = () => run(async () => { await api('/api/logout', {}); showLogin(); message('Signed out.'); });
$('refresh').onclick = () => run(refresh);
$('switch').onclick = () => run(async () => { if (!confirm(enabled ? 'Stop all sponsor delivery?' : 'Enable approved campaigns within their scheduled dates?')) return; await api('/api/switch', { enabled: !enabled }); await refresh(); });
function localDate(date) { return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16); }
const form = $('create-form'); form.elements.starts.value = localDate(new Date()); form.elements.ends.value = localDate(new Date(Date.now() + 7 * 86400_000));
let objectUrl;
$('file').onchange = () => { if (objectUrl) URL.revokeObjectURL(objectUrl); const file = $('file').files[0]; $('draft-preview').hidden = !file; if (file) { objectUrl = URL.createObjectURL(file); $('draft-image').src = objectUrl; } };
form.elements.sponsor.oninput = () => { $('draft-name').textContent = form.elements.sponsor.value; };
form.onsubmit = event => { event.preventDefault(); run(async () => {
  const file = $('file').files[0]; if (!file || file.size > 2 * 1024 * 1024) throw Error('Choose a PNG/JPEG under 2 MB.');
  const image = await new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result.split(',')[1]); reader.onerror = reject; reader.readAsDataURL(file); });
  await api('/api/campaigns', { sponsor: form.elements.sponsor.value, destination: form.elements.destination.value, amount: form.elements.amount.value, starts: new Date(form.elements.starts.value).getTime(), ends: new Date(form.elements.ends.value).getTime(), image });
  form.elements.sponsor.value = ''; form.elements.destination.value = ''; $('file').value = ''; $('draft-preview').hidden = true;
  if (objectUrl) URL.revokeObjectURL(objectUrl); await refresh(); message('Draft saved. Confirm payment or record a complimentary reason, then approve.');
}); };
async function refresh() {
  const data = await api('/api/campaigns'); showDashboard(); enabled = data.enabled;
  $('delivery').textContent = enabled ? 'On' : 'Stopped'; $('switch').textContent = enabled ? 'Stop all ads' : 'Enable ads';
  $('count-live').textContent = data.campaigns.filter(c => c.live).length; $('count-review').textContent = data.campaigns.filter(c => c.status === 'pending').length;
  $('campaigns').replaceChildren(); $('empty').hidden = data.campaigns.length > 0;
  for (const c of data.campaigns) {
    const fragment = $('campaign-template').content.cloneNode(true), card = fragment.querySelector('article');
    card.querySelector('h3').textContent = c.sponsor;
    const expired = c.ends <= Date.now();
    card.querySelector('.status').textContent = expired ? 'Expired' : c.live ? 'Live' : c.status === 'approved' ? (c.starts > Date.now() ? 'Scheduled' : 'Delivery stopped') : c.status;
    card.querySelector('.sponsor-label').textContent = `Sponsored · ${c.sponsor}`;
    card.querySelector('img').src = `/v1/images/${c.id}`;
    const link = card.querySelector('.destination'); link.textContent = c.destination; link.href = c.destination;
    card.querySelector('.dates').textContent = `${new Date(c.starts).toLocaleString()} → ${new Date(c.ends).toLocaleString()}`;
    card.querySelector('.payment').textContent = c.complimentary_reason ? `Complimentary / test · No payment required · ${c.complimentary_reason}` : `${c.amount} LBC · ${c.txid ? 'Payment manually confirmed' : 'Payment not confirmed'}`;
    card.querySelector('.payment-details').hidden = !!(c.txid || c.complimentary_reason) || Number(c.amount) <= 0 || c.status !== 'pending' || expired;
    card.querySelector('.complimentary-details').hidden = !!(c.txid || c.complimentary_reason) || c.status !== 'pending' || expired;
    card.querySelector('.complimentary-form').onsubmit = event => { event.preventDefault(); run(async () => { await api(`/api/campaigns/${c.id}/complimentary`, { reason: event.target.elements.reason.value }); await refresh(); message('Complimentary reason recorded. Click Approve when ready.'); }); };
    card.querySelector('.payment-form').onsubmit = event => { event.preventDefault(); run(async () => { await api(`/api/campaigns/${c.id}/payment`, { txid: event.target.elements.txid.value, confirmed: event.target.elements.confirmed.checked }); await refresh(); message('Payment recorded. The banner still needs your approval.'); }); };
    for (const action of ['approve', 'pause', 'reject']) {
      const button = card.querySelector(`.${action}`);
      button.hidden = action === 'approve' ? !((c.txid || c.complimentary_reason) && ['pending', 'paused'].includes(c.status) && !expired) : action === 'pause' ? c.status !== 'approved' : c.status === 'rejected';
      if (action === 'approve' && c.status === 'paused') button.textContent = 'Resume';
      button.onclick = () => run(async () => { if (!confirm(`${action === 'approve' ? 'Approve this banner and destination' : action === 'pause' ? 'Pause this campaign' : 'Permanently reject/remove this campaign'} for ${c.sponsor}?`)) return; await api(`/api/campaigns/${c.id}/${action}`, {}); await refresh(); message('Campaign updated. App refreshes take up to about one minute.'); });
    }
    $('campaigns').append(fragment);
  }
  await refreshMetrics();
  $('audit').replaceChildren(); const names = new Map(data.campaigns.map(c => [c.id, c.sponsor]));
  for (const entry of data.audit) { const li = document.createElement('li'); li.textContent = `${new Date(entry.created).toLocaleString()} · ${entry.action}${entry.campaign ? ' · ' + (names.get(entry.campaign) || entry.campaign) : ''}`; $('audit').append(li); }
}
run(async () => { try { csrf = (await api('/api/session')).csrf; await refresh(); } catch { showLogin(); } });

async function refreshMetrics() {
  const requestNumber = ++metricsRequest;
  const period = $('metrics-period').value, date = $('metrics-date').value;
  const query = new URLSearchParams({ period }); if (date) query.set('date', date);
  const report = await api('/api/analytics?' + query);
  if (requestNumber !== metricsRequest) return;
  if (!date) $('metrics-date').value = report.date;
  const last = new Date(report.end + 'T00:00:00Z'); last.setUTCDate(last.getUTCDate() - 1);
  $('metrics-range').textContent = `${report.start} through ${last.toISOString().slice(0,10)} · Eastern Time · Refresh to see new events`;
  $('metrics-rows').replaceChildren();
  for (const campaign of report.campaigns) {
    const tr = document.createElement('tr');
    for (const value of [campaign.sponsor, campaign.impressions.toLocaleString(), campaign.clicks.toLocaleString(), campaign.impressions ? (campaign.clicks / campaign.impressions * 100).toFixed(2) + '%' : '—']) {
      const td = document.createElement('td'); td.textContent = value; tr.append(td);
    }
    $('metrics-rows').append(tr);
  }
  if (!report.campaigns.length) { const tr = document.createElement('tr'), td = document.createElement('td'); td.colSpan = 4; td.textContent = 'Create a campaign to start measuring impressions and clicks.'; tr.append(td); $('metrics-rows').append(tr); }
}
$('metrics-period').onchange = () => run(refreshMetrics);
$('metrics-date').onchange = () => run(refreshMetrics);
$('refresh-metrics').onclick = () => run(refreshMetrics);

$('password-form').onsubmit = event => {
 event.preventDefault(); run(async () => {
  if ($('new-password').value !== $('confirm-password').value) throw Error('The new passwords do not match.');
  $('save-password').disabled = true;
  try {
   await api('/api/password', {currentPassword:$('current-password').value,newPassword:$('new-password').value});
   $('password-form').reset(); showLogin(); message('Password changed. All sessions are signed out. Sign in with your new password.');
  } finally { $('save-password').disabled = false; }
 });
};
