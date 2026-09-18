const $ = (id) => document.getElementById(id);

function tablePlayers(data) {
  const raw = data.players && data.players.length
    ? data.players
    : [data.you, data.opponent].filter(Boolean);
  const seen = new Set();
  const list = [];
  for (const p of raw) {
    if (!p || seen.has(p.id)) continue;
    seen.add(p.id);
    list.push(p);
  }
  return list;
}

function teamColor(p) {
  return p && p.color ? p.color : "green";
}

function displayName(p, fallback) {
  if (p && p.human) return "You";
  if (p && p.corporation && p.corporation !== "Unknown") return p.corporation;
  return (p && p.name) || fallback || "Player";
}

function boardTitle(p) {
  if (p && p.human) return "You";
  if (p && p.corporation && p.corporation !== "Unknown") return p.corporation;
  return (p && p.name) || "Player";
}

function boardSubtitle(p) {
  if (!p) return "";
  const bits = [];
  if (p.human && p.corporation && p.corporation !== "Unknown") bits.push(p.corporation);
  if (!p.human && p.name && p.name !== boardTitle(p)) bits.push(p.name);
  if (p.human) bits.push("You");
  return bits.join(" · ");
}

function renderCubes(p) {
  const rows = [
    ["M€", p.megaCredits, p.megaCreditProd],
    ["Steel", p.steel, p.steelProd],
    ["Titanium", p.titanium, p.titaniumProd],
    ["Plant", p.plants, p.plantProd],
    ["Energy", p.energy, p.energyProd],
    ["Heat", p.heat, p.heatProd],
  ];
  return `<table class="cubes">
    <tr><th>Resource</th><th class="num">Qty</th><th class="num">Prod</th></tr>
    ${rows.map(([n, q, pr]) => `<tr><td>${n}</td><td class="num">${q ?? 0}</td><td class="num prod">${pr ?? 0}</td></tr>`).join("")}
  </table>
  <p class="awards">TR ${p.tr ?? 20} · Cities on Mars ${p.citiesOnMars ?? 0} · Greeneries ${p.greeneries ?? 0} · Oceans ${p.oceans ?? 0}</p>`;
}

function renderTags(tags) {
  if (!tags) return "";
  return `<div class="tags">${Object.entries(tags).map(([k, v]) =>
    `<span class="tag ${v ? "" : "zero"}">${k} ${v}</span>`
  ).join("")}</div>`;
}

function extra(text) {
  return (text || "").replace(/\n/g, " · ").replace(/\s+/g, " ").trim();
}

function tokenChip(c) {
  if (!c.tokenType && !(c.tokens > 0)) return "";
  const kind = c.tokenType || "token";
  const n = c.tokens ?? 0;
  const label = n === 1 ? kind : kind + "s";
  return `<span class="token token-${kind}${n ? "" : " zero"}" title="${n} ${label} on this card">${n}</span>`;
}

function renderCards(title, cards, cls) {
  if (!cards || !cards.length) return "";
  return `<h3 class="section-title">${title}</h3>
    <div class="cards">${cards.map((c) => `
      <div class="card ${cls}">
        <div class="card-head">
          <h3>${c.name}</h3>
          ${tokenChip(c)}
        </div>
        <p>${(c.tags || []).join(", ")}${c.extra ? " — " + extra(c.extra) : ""}</p>
      </div>`).join("")}</div>`;
}

function renderPlayer(p) {
  const awards = [...(p.milestones || []), ...(p.awards || [])];
  const color = teamColor(p);
  const you = !!p.human;
  return `<article class="board color-${color}${you ? " you" : ""}">
    <h2>${boardTitle(p)}</h2>
    <p class="corp">${boardSubtitle(p)}</p>
    ${renderCubes(p)}
    ${renderTags(p.tags)}
    ${awards.length ? `<p class="awards">${awards.join(" · ")}</p>` : ""}
    ${renderCards("Blue cards", p.blueCards, "blue")}
    ${renderCards("Automated", p.greenCards, "green")}
    ${renderCards("Events", p.events, "red")}
  </article>`;
}

function setLiveStatus(mode) {
  const el = $("live");
  el.classList.toggle("on", mode === "live");
  el.classList.toggle("bad", mode === "disconnected");
  el.textContent = mode === "live" ? "live" : mode === "disconnected" ? "disconnected" : "idle";
}

function placingLabel(raw) {
  if (!raw) return "";
  const s = String(raw).replace(/[_-]+/g, " ").trim();
  const lower = s.toLowerCase();
  if (lower.includes("ocean") || lower === "aquifer") return "Ocean";
  if (lower.includes("greenery") || lower.includes("forest")) return "Greenery";
  if (lower.includes("city") || lower === "capital") return "City";
  if (lower.includes("generic") || lower.includes("special")) return "Special tile";
  return s.replace(/\b\w/g, (c) => c.toUpperCase());
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  }[c]));
}

const TAG_GLYPH = {
  building: '<path fill="currentColor" d="M4 20V9l8-5 8 5v11h-6v-6H10v6z"/>',
  space: '<path fill="currentColor" d="M12 3 14.2 9H21l-5.4 3.8L17.8 20 12 15.8 6.2 20l2.2-7.2L3 9h6.8z"/>',
  science: '<circle cx="12" cy="12" r="2.2" fill="currentColor"/><ellipse cx="12" cy="12" rx="9" ry="3.4" fill="none" stroke="currentColor" stroke-width="1.7"/><ellipse cx="12" cy="12" rx="9" ry="3.4" fill="none" stroke="currentColor" stroke-width="1.7" transform="rotate(60 12 12)"/><ellipse cx="12" cy="12" rx="9" ry="3.4" fill="none" stroke="currentColor" stroke-width="1.7" transform="rotate(-60 12 12)"/>',
  power: '<path fill="currentColor" d="M13 2 4 14h7l-1 8 10-14h-7z"/>',
  earth: '<circle cx="12" cy="12" r="8" fill="none" stroke="currentColor" stroke-width="1.8"/><path fill="none" stroke="currentColor" stroke-width="1.6" d="M4 12h16M12 4c3 3 3 13 0 16M12 4c-3 3-3 13 0 16"/>',
  jovian: '<circle cx="12" cy="12" r="7.5" fill="currentColor" opacity=".9"/><path fill="none" stroke="#1a1208" stroke-width="1.4" d="M4 12h16"/>',
  plant: '<path fill="currentColor" d="M12 21V11c6-1 8-6 8-8-7 1-9 5-8 8-6-2-8 2-8 6 4-1 7 0 8 2z"/>',
  microbe: '<circle cx="12" cy="12" r="3.2" fill="currentColor"/><circle cx="6" cy="8" r="1.7" fill="currentColor"/><circle cx="18" cy="9" r="1.5" fill="currentColor"/><circle cx="8" cy="17" r="1.6" fill="currentColor"/><circle cx="17" cy="16" r="1.4" fill="currentColor"/>',
  animal: '<path fill="currentColor" d="M7 11c-2 0-3-2-2.2-3.5S8 6 8.5 8c.7-2 3-3 4.2-1.2C14 5 16.5 6 17.2 8c1.4-1 3.4.2 2.6 2.2-.3 1-1.4 1.5-2.3 1.3C17.8 14 16 17 12.5 17S7.4 14 7 11z"/>',
  city: '<path fill="currentColor" d="M4 20V10h4V6h4v4h2V8h6v12z"/>',
  event: '<path fill="currentColor" d="M13 2 3 13h7l-1 9 12-14h-7z"/>',
  venus: '<circle cx="12" cy="10" r="5.2" fill="none" stroke="currentColor" stroke-width="1.8"/><path fill="none" stroke="currentColor" stroke-width="1.8" d="M12 15v6M9 19h6"/>',
  wild: '<path fill="currentColor" d="M12 3 14 9h6l-5 3.6L17 19l-5-3.4L7 19l2-6.4L4 9h6z"/>',
  mars: '<circle cx="10" cy="13" r="6" fill="none" stroke="currentColor" stroke-width="1.8"/><path fill="none" stroke="currentColor" stroke-width="1.8" d="M14 9 20 3M15 3h5v5"/>',
  moon: '<path fill="currentColor" d="M14 4a8 8 0 1 0 6 12 7 7 0 0 1-6-12z"/>',
};

function tagIcon(tag) {
  const key = String(tag || "").toLowerCase();
  const glyph = TAG_GLYPH[key];
  const cls = TAG_GLYPH[key] ? key : "unknown";
  const inner = glyph || `<text x="12" y="16" text-anchor="middle" font-size="11" fill="currentColor">${escapeHtml(key.slice(0, 1).toUpperCase())}</text>`;
  return `<span class="sym tag-sym tag-${cls}" title="${escapeHtml(key)}"><svg viewBox="0 0 24 24">${inner}</svg></span>`;
}

function costSym(n) {
  return `<span class="sym cost-sym" title="Cost ${n} M€">${n}</span>`;
}

function resKey(raw) {
  const k = String(raw || "").toLowerCase();
  if (k === "titanium" || k === "ti") return "ti";
  if (k === "megacredit" || k === "mc" || k === "mé" || k === "m€") return "mc";
  return k;
}

function resLabel(key) {
  return ({
    mc: "M€",
    steel: "steel",
    ti: "titanium",
    plant: "plant",
    energy: "energy",
    heat: "heat",
    tr: "TR",
    o2: "O₂",
    temp: "temp",
    ocean: "ocean",
    card: "card",
    vp: "VP",
  })[key] || key;
}

function resSym(rawKey, value, prod) {
  const key = resKey(rawKey);
  const n = Number(value);
  const shown = Number.isFinite(n) ? (n > 0 ? "+" + n : String(n)) : String(value);
  return `<span class="res-sym res-${key}${prod ? " prod" : ""}" title="${prod ? "Production " : ""}${shown} ${resLabel(key)}"><span class="res-box"></span>${shown}</span>`;
}

function benefitHtml(play) {
  const bits = [];
  for (const [k, v] of Object.entries(play.resources || {})) {
    if (v != null && v !== 0) bits.push(resSym(k, v, false));
  }
  for (const [k, v] of Object.entries(play.production || {})) {
    if (v != null && v !== 0) bits.push(resSym(k, v, true));
  }
  if (play.vp) bits.push(resSym("vp", play.vp, false));
  if (bits.length) return bits.join("");
  if (play.effect) {
    const text = play.effect.length > 110 ? play.effect.slice(0, 107) + "…" : play.effect;
    return `<span class="banner-benefit-text">${escapeHtml(text)}</span>`;
  }
  return "";
}

function applyBannerOpen() {
  $("banner-details").hidden = !bannerOpen;
  $("banner-toggle").setAttribute("aria-expanded", bannerOpen ? "true" : "false");
  const benefit = $("banner-benefit");
  const has = benefit.innerHTML.trim().length > 0;
  benefit.hidden = bannerOpen || !has;
}

function bindBannerUi() {
  if (bannerUiBound) return;
  bannerUiBound = true;
  $("banner-toggle").addEventListener("click", () => {
    if ($("banner-toggle").disabled) return;
    bannerOpen = !bannerOpen;
    applyBannerOpen();
  });
}

function renderBanner(data) {
  const play = data.activePlay;
  const banner = $("banner");
  banner.classList.remove("yours", "color-green", "color-yellow", "color-red", "color-blue", "color-black");
  const key = play && play.cardName ? `${play.playerId}:${play.cardName}` : "";
  if (key !== bannerKey) {
    bannerKey = key;
    bannerOpen = !!key;
  }

  const placing = placingLabel(play && play.placing);
  const placingEl = $("banner-placing");
  if (placing) {
    placingEl.hidden = false;
    placingEl.textContent = "Placing " + placing;
  } else {
    placingEl.hidden = true;
    placingEl.textContent = "";
  }

  if (play && play.cardName) {
    const playColor = play.playerColor || (play.yours ? teamColor(data.you) : teamColor((data.opponents || [])[0]));
    banner.classList.add("color-" + (playColor || "green"));
    if (play.yours) banner.classList.add("yours");
    $("banner-toggle").disabled = false;
    $("banner-chevron").hidden = false;
    $("banner-kicker").textContent = `${play.yours ? "You" : play.playerLabel}${play.colorLabel ? " · " + play.colorLabel : ""}`;
    $("banner-line").innerHTML = `<span class="banner-name">${escapeHtml(play.cardName)}</span>${
      play.cost != null ? costSym(play.cost) : ""
    }${(play.tags || []).map(tagIcon).join("")}`;
    $("banner-benefit").innerHTML = benefitHtml(play);
    $("banner-effect").textContent = play.effect || "";
    $("banner-tips").innerHTML = (play.remember || []).map((t) => `<li>${escapeHtml(t)}</li>`).join("");
  } else {
    $("banner-toggle").disabled = true;
    $("banner-chevron").hidden = true;
    $("banner-kicker").textContent = "No card in flight";
    $("banner-line").textContent = "";
    $("banner-benefit").innerHTML = "";
    $("banner-effect").textContent = "";
    $("banner-tips").innerHTML = "";
    bannerOpen = false;
  }
  applyBannerOpen();
}

function render(data) {
  $("meta").textContent = `Gen ${data.generation ?? "?"} · ${data.phase || ""} · ${data.board || ""} · ${data.gameId ? "Game " + data.gameId : "no game yet"}`;
  setLiveStatus(data.live ? "live" : "idle");
  if (data.url) {
    $("qr-link").href = data.url;
    $("qr-link").title = data.url;
    $("qr-url").textContent = data.url.replace(/^https?:\/\//, "").replace(/\/$/, "");
  }

  renderBanner(data);

  const players = tablePlayers(data);
  const boards = $("boards");
  boards.className = "boards players-" + Math.max(1, Math.min(5, players.length));
  boards.innerHTML = players.map(renderPlayer).join("");
  renderScore(data, players);
}

function scoreOf(data, p) {
  const byId = (data.score && data.score.byId) || {};
  return byId[p.id] || byId[String(p.id)] || (p.human ? data.score?.you : {}) || {};
}

function creditsOf(p, b) {
  return b.mc ?? p.megaCredits ?? 0;
}

function renderScore(data, players) {
  const box = $("scoreboard");
  const score = data.score;
  if (!score || !players.length) {
    box.hidden = true;
    return;
  }
  box.hidden = false;
  const breakdowns = players
    .map((p) => ({ p, b: scoreOf(data, p) }))
    .sort((a, c) => (c.b.total ?? 0) - (a.b.total ?? 0)
      || creditsOf(c.p, c.b) - creditsOf(a.p, a.b)
      || a.p.id - c.p.id);
  const topVp = breakdowns[0]?.b.total ?? 0;
  const vpTie = breakdowns.filter(({ b }) => (b.total ?? 0) === topVp).length > 1;
  const winnerId = breakdowns[0]?.p.id;
  $("score-line").innerHTML = breakdowns.map(({ p, b }, i) => {
    const name = displayName(p, "P" + p.id);
    const ahead = p.id === winnerId && topVp > 0;
    const sep = i === 0 ? "" : `<span class="score-vs">—</span>`;
    const mc = vpTie ? costSym(creditsOf(p, b)) : "";
    return `${sep}<span class="score-chip color-${teamColor(p)}${ahead ? " ahead" : ""}"><span class="name">${name}</span> <strong>${b.total ?? 0}</strong>${mc}</span>`;
  }).join("") + `<span class="chev" aria-hidden="true"><svg viewBox="0 0 24 24"><path fill="currentColor" d="M7.4 8.6 12 13.2l4.6-4.6 1.4 1.4-6 6-6-6z"/></svg></span>`;

  $("score-head").innerHTML = `<tr><th></th>${breakdowns.map(({ p }) =>
    `<th class="team color-${teamColor(p)}">${displayName(p, "P" + p.id)}</th>`
  ).join("")}</tr>`;

  const rows = [
    ["TR", (b) => b.tr, false],
    ["Milestones", (b) => b.milestones, false],
    ["Awards", (b) => b.awards, false],
    ["Greeneries", (b) => b.greeneries, false],
    ["Cities", (b) => b.cities, false],
    ["Cards + tokens", (b) => b.cards, true],
    ["<span class=\"res-sym res-mc\" title=\"M€\"><span class=\"res-box\"></span></span> tiebreak", (b) => b.mc, false],
  ];
  $("score-lines").innerHTML = rows.map(([n, pick, drill]) =>
    `<tr${drill ? ` class="clickable${cardsOpen ? " open" : ""}" data-drill="cards" title="Click for card VP"` : ""}><td>${n}</td>${
      breakdowns.map(({ b }) => `<td>${pick(b) ?? 0}</td>`).join("")
    }</tr>`
  ).join("");

  $("score-cards").innerHTML = breakdowns.map(({ p, b }) => `
    <div class="color-${teamColor(p)}">
      <h3 class="section-title">${displayName(p, "P" + p.id)} cards</h3>
      <ul>${(b.cardDetails || []).map((d) => `<li>${d}</li>`).join("") || "<li>None</li>"}</ul>
    </div>`).join("");
  const notes = [];
  if (score.note) notes.push(score.note);
  if (vpTie && topVp > 0) notes.push("VP tie — most credits win.");
  $("score-note").textContent = notes.join(" ");
  $("score-note").hidden = notes.length === 0;
  $("score-details").hidden = !scoreOpen;
  $("score-cards").hidden = !cardsOpen;
  $("score-toggle").setAttribute("aria-expanded", scoreOpen ? "true" : "false");
}

let scoreOpen = false;
let cardsOpen = false;
let scoreUiBound = false;
let bannerOpen = true;
let bannerKey = "";
let bannerUiBound = false;

function bindScoreUi() {
  if (scoreUiBound) return;
  scoreUiBound = true;
  $("score-toggle").addEventListener("click", () => {
    scoreOpen = !scoreOpen;
    if (!scoreOpen) cardsOpen = false;
    $("score-details").hidden = !scoreOpen;
    $("score-cards").hidden = !cardsOpen;
    $("score-toggle").setAttribute("aria-expanded", scoreOpen ? "true" : "false");
  });
  $("score-lines").addEventListener("click", (ev) => {
    const row = ev.target.closest("tr[data-drill='cards']");
    if (!row) return;
    cardsOpen = !cardsOpen;
    $("score-cards").hidden = !cardsOpen;
    row.classList.toggle("open", cardsOpen);
  });
}

async function tick() {
  try {
    const res = await fetch("/api/state", { cache: "no-store" });
    if (!res.ok) throw new Error(res.status);
    render(await res.json());
  } catch (err) {
    setLiveStatus("disconnected");
  }
}

tick();
setInterval(tick, 500);
bindScoreUi();
bindBannerUi();
