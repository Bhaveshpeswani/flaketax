'use strict';

const el = id => document.getElementById(id);
let login = null;
let current = null;
let busy = false;
let photoUrl = null;
function clearPhoto() {
    if (photoUrl) URL.revokeObjectURL(photoUrl);
    photoUrl = null;
    el('photoImage').removeAttribute('src');
    el('photoPreview').hidden = true;
}

function show(text, type = '') {
    el('message').textContent = text;
    el('message').className = 'message ' + type;
}
function authHeader() {
    if (!login) throw new Error('Sign in first');
    const bytes = new TextEncoder().encode(login.handle + ':' + login.password);
    return 'Basic ' + btoa(String.fromCharCode(...bytes));
}
async function api(url, method = 'GET', body = null, publicCall = false) {
    const headers = { Accept: 'application/json' };
    if (!publicCall) headers.Authorization = authHeader();
    if (body !== null && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
    const res = await fetch(url, { method, headers, cache: 'no-store',
        body: body === null ? undefined : body instanceof FormData ? body : JSON.stringify(body) });
    if (res.status === 204) return null;
    const text = await res.text();
    let data;
    try { data = text ? JSON.parse(text) : null; }
    catch { data = text; }
    if (!res.ok) { const error = new Error(data && data.error || `HTTP ${res.status}`); error.status = res.status; throw error; }
    return data;
}
async function act(fn, ok = 'Saved') {
    if (busy) return;
    busy = true;
    document.querySelectorAll('button').forEach(button => button.disabled = true);
    show('Working...');
    try { await fn(); show(ok, 'success'); }
    catch (error) { show(error.message || 'Request failed', 'error'); }
    finally { busy = false; refreshButtons(); }
}
function fresh() {
    clearPhoto();
    login = null;
    current = null;
    el('password').value = '';
    el('who').textContent = 'Not signed in';
    el('walletValue').textContent = 'Sign in to see balance';
    el('pactDetail').hidden = true;
    el('history').hidden = true;
    el('friendList').replaceChildren();
    el('pactList').replaceChildren();
    refreshButtons();
}
function clockLabel(iso) { return new Date(iso).toLocaleString(); }
function refreshTimer() {
    if (!current) return;
    const end = Date.parse(current.status === 'OPEN' && Date.now() < Date.parse(current.deadline)
        ? current.deadline : current.reviewEnd);
    const s = Math.max(0, Math.ceil((end - Date.now()) / 1000));
    const label = current.status !== 'OPEN' ? 'Finished'
        : s === 0 ? 'Review or settlement ready'
            : `${Math.floor(s/60)}m ${String(s % 60).padStart(2,'0')}s`;
    el('countdown').textContent = label;
    refreshButtons();
}
function refreshButtons() {
    const signed = Boolean(login);
    el('logout').hidden = !signed;
    el('closeAccount').hidden = !signed;
    if (busy) return;
    document.querySelectorAll('button').forEach(button => button.disabled = false);
    const host = signed && current && current.host === login.handle;
    const open = current && current.status === 'OPEN';
    el('hostTools').hidden = !host;
    el('settle').disabled = !host || !open || Date.now() < Date.parse(current.reviewEnd);
    el('cancel').disabled = !host || !open;
    el('archive').disabled = !host || open || current.archived;
    el('timeForm').hidden = !host || !open ||
        current.members.some(m => m.handle !== current.host || m.status !== 'JOINED');
}
async function loadAccount() {
    const me = await api('/api/me');
    el('who').textContent = 'Signed in as ' + me.handle;
    el('walletValue').textContent = me.balance.toLocaleString() + ' points';
}
function append(tag, text, className = '') {
    const node = document.createElement(tag);
    node.textContent = text;
    if (className) node.className = className;
    return node;
}
function line(parent, title, meta, buttons) {
    const row = append('div','', 'compact-row');
    const info = document.createElement('div');
    info.append(append('strong', title), append('div', meta, 'meta'));
    row.append(info);
    const actions = append('div','', 'actions');
    for (const [name, fn] of buttons) {
        const button = append('button', name, 'outline');
        button.type = 'button';
        button.addEventListener('click', fn);
        actions.append(button);
    }
    row.append(actions);
    parent.append(row);
}
function renderList(items, lobby = false) {
    const list = el('pactList');
    list.replaceChildren();
    if (!items.length) { list.textContent = 'No pacts in this list.'; return; }
    for (const p of items) {
        const buttons = [[lobby ? 'View or join' : 'Open', () => act(async () => {
            let detail;
            try { detail = await api(`/api/pacts/${p.id}`); }
            catch (error) {
                if (!lobby || error.status !== 403) throw error;
                detail = await api(`/api/pacts/${p.id}/join`, 'POST');
            }
            draw(detail); await loadAccount();
        }, 'Pact opened.')]];
        line(list, `#${p.id}  ${p.title}`, `${p.host}, ${p.stake} points, ${p.status || 'OPEN'}, closes ${clockLabel(p.deadline)}`, buttons);
    }
}
function draw(p) {
    clearPhoto();
    current = p;
    el('pactDetail').hidden = false;
    el('pactId').value = p.id;
    el('detailTitle').textContent = p.title;
    el('pactNumber').textContent = `Pact #${p.id}`;
    el('pactStatus').textContent = p.status;
    el('pactHost').textContent = p.host;
    el('pactStake').textContent = `${p.stake} points`;
    el('pactPot').textContent = `${p.pot} points`;
    el('pactDeadline').textContent = clockLabel(p.deadline);
    el('pactReview').textContent = clockLabel(p.reviewEnd);
    const rows = el('members');
    rows.replaceChildren();
    for (const member of p.members) {
        const tr = document.createElement('tr');
        tr.append(append('td',member.handle), append('td',member.status),
            append('td', member.proof || 'No note'));
        const photo = document.createElement('td');
        if (member.hasPhoto) {
            const button = append('button','View image','outline');
            button.type = 'button';
            button.addEventListener('click',()=>act(async()=>{
                const res = await fetch(`/api/pacts/${p.id}/proof/${member.userId}/photo`,
                    {headers: {Authorization: authHeader()}, cache: 'no-store'});
                if (!res.ok) throw new Error(`Unable to view image: ${res.status}`);
                const blob = await res.blob();
                clearPhoto();
                photoUrl = URL.createObjectURL(blob);
                el('photoImage').src = photoUrl;
                el('photoPreview').hidden = false;
            },'Image opened.'));
            photo.append(button);
        } else photo.textContent = 'None';
        tr.append(photo);
        rows.append(tr);
    }
    if (!p.members.length) {
        const tr = document.createElement('tr'), td = append('td','No players yet');
        td.colSpan = 4; tr.append(td); rows.append(tr);
    }
    const myself = login && p.members.find(m => m.handle === login.handle);
    el('proofForm').hidden = !myself || myself.status !== 'JOINED' ||
        p.status !== 'OPEN' || Date.now() >= Date.parse(p.deadline);
    const available = p.members.filter(m => m.status === 'SUBMITTED' &&
        (m.handle === p.host
            ? login && login.handle !== p.host && Boolean(myself)
            : login && login.handle === p.host));
    el('reviewForm').hidden = !available.length || p.status !== 'OPEN' ||
        Date.now() >= Date.parse(p.reviewEnd);
    const pick = el('playerId');
    pick.replaceChildren();
    for (const m of available) {
        const option = document.createElement('option');
        option.value = m.userId;
        option.textContent = m.handle;
        pick.append(option);
    }
    el('settleNote').textContent = p.status === 'OPEN'
        ? `The server can settle after ${clockLabel(p.reviewEnd)}. The countdown is display only.`
        : 'This pact is finished. Its points history is kept.';
    refreshTimer();
}
function selectedId() {
    const id = Number(el('pactId').value || current && current.id);
    if (!Number.isSafeInteger(id) || id < 1) throw new Error('Enter a pact number');
    return id;
}
async function loadFriends() {
    const data = await api('/api/friends');
    const box = el('friendList');
    box.replaceChildren();
    if (!data.length) { box.textContent = 'No friends or requests yet.'; return; }
    for (const f of data) {
        const actions = [];
        if (f.state === 'INCOMING') actions.push(['Accept',()=>act(async()=>{
            await api(`/api/friends/${encodeURIComponent(f.handle)}/accept`, 'POST');
            await loadFriends();
        },'Friend added.')]);
        actions.push(['Remove',()=>act(async()=>{
            await api(`/api/friends/${encodeURIComponent(f.handle)}`, 'DELETE');
            await loadFriends();
        },'Removed.')]);
        line(box, f.handle, f.state.toLowerCase(), actions);
    }
}
function renderEntries(entries) {
    const rows = el('ledgerRows');
    rows.replaceChildren();
    for (const e of entries) {
        const tr = document.createElement('tr');
        tr.append(append('td', `${e.kind.toLowerCase()} ${e.pactId ? `on pact #${e.pactId}` : ''}`),
            append('td', `${e.amount > 0 ? '+' : ''}${e.amount}`, 'number'));
        rows.append(tr);
    }
    el('history').hidden = false;
}

el('loginForm').addEventListener('submit',e=>{e.preventDefault();act(async()=>{
    login={handle:el('handle').value.trim().toLowerCase(),password:el('password').value};
    try { await loadAccount(); await loadFriends(); }
    catch (err) { fresh(); throw err; }
},'Signed in.');});
el('signup').addEventListener('click',()=>act(async()=>{
    const handle=el('handle').value.trim().toLowerCase(), password=el('password').value;
    await api('/api/users','POST',{handle,password},true);
    login={handle,password}; await loadAccount();
},'Account created.'));
el('logout').addEventListener('click',()=>{fresh();show('Signed out.');});
el('closePhoto').addEventListener('click', clearPhoto);
el('closeAccount').addEventListener('click',()=>{
    if(prompt('Type CLOSE to close your account. Ledger history remains.')!=='CLOSE') return;
    act(async()=>{await api('/api/me','DELETE');fresh();},'Account closed.');
});
el('friendForm').addEventListener('submit',e=>{e.preventDefault();act(async()=>{
    await api(`/api/friends/${encodeURIComponent(el('friendName').value.trim().toLowerCase())}`,'POST');
    await loadFriends();
},'Friend request sent.');});
el('loadFriends').addEventListener('click',()=>act(loadFriends,'Friends refreshed.'));
el('wallet').addEventListener('click',()=>act(async()=>{
    await loadAccount();renderEntries(await api('/api/me/entries'));
},'Point history loaded.'));
el('pactForm').addEventListener('submit',e=>{e.preventDefault();act(async()=>{
    const minutes=Number(el('duration').value);
    if(!Number.isInteger(minutes)||minutes<1||minutes>43200) throw new Error('Choose 1 to 43200 minutes');
    const p=await api('/api/pacts','POST',{
        title:el('title').value.trim(), stake:Number(el('stake').value),
        deadline:new Date(Date.now()+minutes*60000).toISOString()
    });
    draw(p);await loadAccount();
},'Pact created. You joined automatically and staked your points.');});
el('openPacts').addEventListener('click',()=>act(async()=>renderList(await api('/api/pacts/open'),true),'Open pacts loaded.'));
el('friendPacts').addEventListener('click',()=>act(async()=>renderList(await api('/api/pacts/friends'),true),"Friends' pacts loaded."));
el('mine').addEventListener('click',()=>act(async()=>renderList(await api('/api/pacts')),'My pacts loaded.'));
el('lookupForm').addEventListener('submit',e=>{e.preventDefault();act(async()=>draw(await api(`/api/pacts/${selectedId()}`)),'Pact opened.');});
el('join').addEventListener('click',()=>act(async()=>{
    draw(await api(`/api/pacts/${selectedId()}/join`,'POST'));
    await loadAccount();
},'Joined the pact.'));
el('proofForm').addEventListener('submit',e=>{e.preventDefault();act(async()=>{
    const form=new FormData();
    form.append('note',el('note').value.trim());
    if(el('photo').files[0]) form.append('photo',el('photo').files[0]);
    draw(await api(`/api/pacts/${selectedId()}/proof/photo`,'POST',form));
    el('photo').value='';
},'Proof submitted.');});
el('reviewForm').addEventListener('submit',e=>{e.preventDefault();act(async()=>{
    draw(await api(`/api/pacts/${selectedId()}/review/${Number(el('playerId').value)}`,'POST',
        {approved:el('approved').value==='true'}));
},'Proof reviewed.');});
el('timeForm').addEventListener('submit',e=>{e.preventDefault();act(async()=>{
    draw(await api(`/api/pacts/${selectedId()}/deadline`,'PATCH',
        {minutes:Number(el('newMinutes').value)}));
},'Deadline updated.');});
el('settle').addEventListener('click',()=>act(async()=>{
    draw(await api(`/api/pacts/${selectedId()}/settle`,'POST'));
    await loadAccount();
},'Pact settled.'));
el('cancel').addEventListener('click',()=>{
    if(!confirm('Cancel this pact and refund all players?'))return;
    act(async()=>{draw(await api(`/api/pacts/${selectedId()}/cancel`,'POST'));await loadAccount();},'Pact cancelled, points refunded.');
});
el('archive').addEventListener('click',()=>{
    if(!confirm('Remove this pact from lists? Its ledger remains.'))return;
    act(async()=>{await api(`/api/pacts/${selectedId()}`,'DELETE');el('pactDetail').hidden=true;current=null;
        renderList(await api('/api/pacts'));},'Pact removed from lists.');
});
setInterval(refreshTimer,1000);
refreshButtons();