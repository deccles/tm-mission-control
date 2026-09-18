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
  return p && p.color ? p.color : "blue";
}

function displayName(p, fallback) {
  if (p && p.human) return "You";
  if (p && p.corporation && p.corporation !== "Unknown") return p.corporation;
  return (p && p.name) || fallback || "Player";
}

function boardTitle(p) {
  if (p && p.corporation && p.corporation !== "Unknown") return p.corporation;
  if (p && p.human) return "You";
  return (p && p.name) || "Player";
}

function boardSubtitle(p) {
  if (!p) return "";
  if (p.human && p.corporation && p.corporation !== "Unknown") return "You";
  if (!p.human && p.name && p.name !== boardTitle(p)) return p.name;
  return "";
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
  return `<div class="board-cubes"><table class="cubes">
    <tr><th>Resource</th><th class="num">Qty</th><th class="num">Prod</th></tr>
    ${rows.map(([n, q, pr]) => `<tr><td>${n}</td><td class="num">${q ?? 0}</td><td class="num prod">${pr ?? 0}</td></tr>`).join("")}
  </table></div>`;
}

function renderTrLine(p) {
  return `<p class="board-tr">TR ${p.tr ?? 20} · Cities on Mars ${p.citiesOnMars ?? 0} · Greeneries ${p.greeneries ?? 0} · Oceans ${p.oceans ?? 0}</p>`;
}

function renderTags(tags) {
  const cells = tags
    ? Object.entries(tags).map(([k, v]) =>
      `<span class="tag-count${v ? "" : " zero"}" title="${escapeHtml(k)} ${v}">${tagIcon(k)}<span class="n">${v}</span></span>`)
    : [];
  return `<div class="tags">${cells.join("")}</div>`;
}

function extra(text) {
  return (text || "").replace(/\n/g, " · ").replace(/\s+/g, " ").trim();
}

function corpRuleText(raw) {
  if (!raw) return "";
  let text = String(raw).replace(/\s+/g, " ").trim();
  text = text.replace(/\s*-{2,}\s*Ed\. note:.*$/i, "").trim();
  const bits = [...text.matchAll(/\((?:Effect|Action):[^)]+\)/gi)]
    .map((m) => m[0].slice(1, -1).trim())
    .filter(Boolean);
  if (bits.length) return bits.join(" ");
  return extra(text);
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
        <p class="card-tags">${(c.tags || []).map(tagIcon).join("")}</p>
        ${c.extra ? `<p>${extra(c.extra)}</p>` : ""}
      </div>`).join("")}</div>`;
}

function helpMark() {
  return `<span class="corp-help" aria-hidden="true"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2"/><text x="12" y="17" text-anchor="middle" font-size="13" font-weight="700" fill="currentColor">?</text></svg></span>`;
}

function chevron() {
  return `<span class="chev" aria-hidden="true"><svg viewBox="0 0 24 24"><path fill="currentColor" d="M7.4 8.6 12 13.2l4.6-4.6 1.4 1.4-6 6-6-6z"/></svg></span>`;
}

function renderPlayer(p) {
  const awards = [...(p.milestones || []), ...(p.awards || [])];
  const color = teamColor(p);
  const you = !!p.human;
  const subtitle = boardSubtitle(p);
  const title = escapeHtml(boardTitle(p));
  const rules = corpRuleText(p.corpRules);
  const rulesOpen = corpOpenId === p.id;
  const collapsed = collapsedBoardIds.has(p.id);
  const info = rules
    ? `<button type="button" class="corp-info" data-corp="${p.id}" aria-expanded="${rulesOpen ? "true" : "false"}" aria-controls="corp-rules-${p.id}" aria-label="${title} corporation rules">
        <span class="corp-name">${title}</span>${helpMark()}
      </button>`
    : `<h2 class="corp-name">${title}</h2>`;
  const rulesBlock = rules
    ? `<p class="corp-rules" id="corp-rules-${p.id}"${rulesOpen && !collapsed ? "" : " hidden"}>${escapeHtml(rules)}</p>`
    : "";
  return `<article class="board color-${color}${you ? " you" : ""}${collapsed ? " collapsed" : ""}">
    <div class="board-head">
      ${info}
      <button type="button" class="board-toggle" data-corp="${p.id}" aria-expanded="${collapsed ? "false" : "true"}" aria-controls="board-body-${p.id}" aria-label="${collapsed ? "Expand" : "Collapse"} ${title}">
        ${chevron()}
      </button>
    </div>
    <div class="board-body" id="board-body-${p.id}"${collapsed ? " hidden" : ""}>
      <div class="board-intro">
        ${subtitle ? `<p class="corp">${escapeHtml(subtitle)}</p>` : ""}
        ${rulesBlock}
      </div>
      ${renderCubes(p)}
      ${renderTrLine(p)}
      ${renderTags(p.tags)}
      <p class="board-awards">${awards.length ? awards.join(" · ") : ""}</p>
      <div class="board-blues">${renderCards("Blue cards", p.blueCards, "blue")}</div>
      <div class="board-rest">
        ${renderCards("Automated", p.greenCards, "green")}
        ${renderCards("Events", p.events, "red")}
      </div>
    </div>
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

const TI_STAR = '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 2.2 14.7 8.6h6.8l-5.5 4.1 2.1 6.7L12 15.8 5.9 19.4l2.1-6.7L2.5 8.6h6.8z"/></svg>';

function resSym(rawKey, value, prod) {
  const key = resKey(rawKey);
  const n = Number(value);
  const shown = Number.isFinite(n) ? (n > 0 ? "+" + n : String(n)) : String(value);
  const mark = key === "ti" ? TI_STAR : "";
  return `<span class="res-sym res-${key}${prod ? " prod" : ""}" title="${prod ? "Production " : ""}${shown} ${resLabel(key)}"><span class="res-box">${mark}</span>${shown}</span>`;
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
  banner.classList.remove("yours", "color-blue", "color-green", "color-purple", "color-yellow", "color-red", "color-black");
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
    banner.classList.add("color-" + (playColor || "blue"));
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
  if (data.gameId !== lastGameId) {
    lastGameId = data.gameId || "";
    corpOpenId = null;
    collapsedBoardIds = new Set();
    selectedGen = null;
    chartsOpen = false;
    cardsOpen = false;
  }
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
  alignBoardSections();
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
  lastScoreData = data;
  lastScorePlayers = players;
  const ended = /endgame/i.test(data.phase || "");
  $("score-kicker").textContent = ended ? "Total VP" : "If the game ended now";
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

  const history = score.history || [];
  const showCharts = (data.generation ?? 0) >= 3 || ended;
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
  renderScoreCharts(data, players, history, ended);
  const notes = [];
  if (score.note) notes.push(score.note);
  if (vpTie && topVp > 0) notes.push("VP tie — most credits win.");
  $("score-note").textContent = notes.join(" ");
  $("score-note").hidden = notes.length === 0;
  $("score-details").hidden = !scoreOpen;
  $("score-cards").hidden = !cardsOpen;
  $("score-chart-panel").hidden = !showCharts;
  $("score-charts").hidden = !chartsOpen || !showCharts;
  $("score-chart-toggle").setAttribute("aria-expanded", chartsOpen ? "true" : "false");
  $("score-toggle").setAttribute("aria-expanded", scoreOpen ? "true" : "false");
}

const TEAM_HEX = { blue: "#3d7ec9", green: "#2f9e44", purple: "#9b59b6", yellow: "#d4b429", red: "#c44532" };

function historyValue(point, playerId, key) {
  const by = point.byId || {};
  const b = by[playerId] || by[String(playerId)] || {};
  return Number(b[key] ?? 0);
}

function renderScoreCharts(data, players, history, ended) {
  const box = $("score-charts");
  if (!history || history.length < 2) {
    box.innerHTML = `<p class="score-note">Not enough generations yet.</p>`;
    return;
  }
  const vpTitle = ended ? "Total VP" : "If the game ended now";
  box.innerHTML = `
    <div class="score-chart-block">
      <h3 class="section-title">TR</h3>
      ${lineChartSvg(history, players, "tr", "TR")}
    </div>
    <div class="score-chart-block">
      <h3 class="section-title">${vpTitle}</h3>
      ${lineChartSvg(history, players, "total", "VP")}
    </div>
    <div class="score-gen" id="score-gen"></div>`;
  renderGenExplain(data, players, history);
}

function lineChartSvg(history, players, key, label) {
  const w = 320;
  const h = 108;
  const padL = 28;
  const padR = 8;
  const padT = 8;
  const padB = 20;
  const n = history.length;
  const xs = history.map((_, i) => padL + (i * (w - padL - padR)) / Math.max(1, n - 1));
  let ymin = Infinity;
  let ymax = -Infinity;
  for (const pt of history) {
    for (const p of players) {
      const v = historyValue(pt, p.id, key);
      ymin = Math.min(ymin, v);
      ymax = Math.max(ymax, v);
    }
  }
  if (!Number.isFinite(ymin) || ymin === ymax) {
    ymin = (ymin || 0) - 1;
    ymax = ymin + 2;
  }
  const yAt = (v) => padT + (1 - (v - ymin) / (ymax - ymin)) * (h - padT - padB);
  const cols = xs.map((x, i) => {
    const gen = history[i].generation;
    const x0 = i === 0 ? padL - 6 : (xs[i - 1] + x) / 2;
    const x1 = i === n - 1 ? w - padR + 6 : (x + xs[i + 1]) / 2;
    const sel = selectedGen === gen ? " selected" : "";
    return `<rect class="chart-col${sel}" data-gen="${gen}" x="${x0.toFixed(1)}" y="0" width="${Math.max(8, x1 - x0).toFixed(1)}" height="${h}"/>`;
  }).join("");
  const lines = players.map((p) => {
    const color = TEAM_HEX[teamColor(p)] || "#b89f88";
    const d = history.map((pt, i) => `${i === 0 ? "M" : "L"}${xs[i].toFixed(1)},${yAt(historyValue(pt, p.id, key)).toFixed(1)}`).join(" ");
    const dots = history.map((pt, i) =>
      `<circle class="chart-dot" cx="${xs[i].toFixed(1)}" cy="${yAt(historyValue(pt, p.id, key)).toFixed(1)}" r="2.6" fill="${color}"/>`
    ).join("");
    return `<path class="chart-line" stroke="${color}" d="${d}"/>${dots}`;
  }).join("");
  const axis = `<text class="chart-axis" x="2" y="${yAt(ymax) + 3}">${Math.round(ymax)}</text>`
    + `<text class="chart-axis" x="2" y="${yAt(ymin) + 3}">${Math.round(ymin)}</text>`
    + xs.map((x, i) => {
      const pt = history[i];
      const tick = pt.now ? "now" : String(pt.generation);
      return `<text class="chart-axis" text-anchor="middle" x="${x.toFixed(1)}" y="${h - 4}">${tick}</text>`;
    }).join("");
  return `<svg class="score-chart-svg" viewBox="0 0 ${w} ${h}" preserveAspectRatio="xMidYMid meet" role="img" aria-label="${label} by generation">${cols}${lines}${axis}</svg>`;
}

function signed(n) {
  if (n > 0) return "+" + n;
  return String(n);
}

function renderGenExplain(data, players, history) {
  const el = $("score-gen");
  if (!el) return;
  if (selectedGen == null) {
    el.innerHTML = `<p class="score-gen-kicker">Tap a generation to see what moved.</p>`;
    return;
  }
  const idx = history.findIndex((pt) => pt.generation === selectedGen);
  if (idx < 0) {
    el.innerHTML = `<p class="score-gen-kicker">Tap a generation to see what moved.</p>`;
    return;
  }
  const cur = history[idx];
  const prev = idx > 0 ? history[idx - 1] : null;
  const events = (data.score.events || []).filter((e) => e.generation === selectedGen);
  const title = cur.now ? `Now (gen ${selectedGen})` : `Generation ${selectedGen}`;
  const buckets = [
    ["TR", "tr"],
    ["Milestones", "milestones"],
    ["Awards", "awards"],
    ["Greeneries", "greeneries"],
    ["Cities", "cities"],
    ["Cards", "cards"],
  ];
  el.innerHTML = `<p class="score-gen-kicker">${title}</p>` + players.map((p) => {
    const total = historyValue(cur, p.id, "total") - (prev ? historyValue(prev, p.id, "total") : 0);
    const parts = buckets.map(([name, key]) => {
      const d = historyValue(cur, p.id, key) - (prev ? historyValue(prev, p.id, key) : 0);
      return d ? `${name} ${signed(d)}` : "";
    }).filter(Boolean);
    const named = events.filter((e) => e.playerId === p.id).map((e) => `<li>${escapeHtml(e.label)}</li>`).join("");
    return `<div class="score-gen-player color-${teamColor(p)}">
      <strong>${escapeHtml(displayName(p, "P" + p.id))} ${signed(total)}</strong>
      ${parts.length ? `<span class="buckets">${parts.join(" · ")}</span>` : ""}
      ${named ? `<ul>${named}</ul>` : ""}
    </div>`;
  }).join("");
}

let scoreOpen = false;
let cardsOpen = false;
let chartsOpen = false;
let selectedGen = null;
let lastScoreData = null;
let lastScorePlayers = [];
let scoreUiBound = false;
let bannerOpen = true;
let bannerKey = "";
let bannerUiBound = false;
let corpOpenId = null;
let collapsedBoardIds = new Set();
let corpUiBound = false;
let lastGameId = "";

function applyBoardUi() {
  document.querySelectorAll(".board").forEach((board) => {
    const toggle = board.querySelector(".board-toggle");
    const info = board.querySelector(".corp-info");
    const id = Number((toggle || info)?.dataset.corp);
    if (!id) return;
    const collapsed = collapsedBoardIds.has(id);
    const rulesOpen = corpOpenId === id;
    board.classList.toggle("collapsed", collapsed);
    const body = board.querySelector(".board-body");
    if (body) body.hidden = collapsed;
    if (toggle) {
      const title = board.querySelector(".corp-name")?.textContent || "board";
      toggle.setAttribute("aria-expanded", collapsed ? "false" : "true");
      toggle.setAttribute("aria-label", `${collapsed ? "Expand" : "Collapse"} ${title}`);
    }
    if (info) info.setAttribute("aria-expanded", rulesOpen ? "true" : "false");
    const rules = board.querySelector(".corp-rules");
    if (rules) rules.hidden = collapsed || !rulesOpen;
  });
  alignBoardSections();
}

const BOARD_ALIGN = [".board-head", ".board-intro", ".board-cubes", ".board-tr", ".tags", ".board-awards"];

function alignBoardSections() {
  document.querySelectorAll(BOARD_ALIGN.join(",")).forEach((el) => {
    el.style.minHeight = "";
  });
  const root = $("boards");
  if (!root) return;
  const colCount = getComputedStyle(root).gridTemplateColumns.split(/\s+/).filter(Boolean).length;
  if (colCount < 2) return;
  const groups = new Map();
  for (const board of root.querySelectorAll(".board:not(.collapsed)")) {
    const top = Math.round(board.offsetTop);
    if (!groups.has(top)) groups.set(top, []);
    groups.get(top).push(board);
  }
  for (const group of groups.values()) {
    if (group.length < 2) continue;
    for (const sel of BOARD_ALIGN) {
      const els = group.map((b) => b.querySelector(sel)).filter(Boolean);
      if (els.length < 2) continue;
      const h = Math.max(...els.map((el) => el.offsetHeight));
      els.forEach((el) => { el.style.minHeight = `${h}px`; });
    }
  }
}

function bindCorpUi() {
  if (corpUiBound) return;
  corpUiBound = true;
  window.addEventListener("resize", alignBoardSections);
  $("boards").addEventListener("click", (ev) => {
    const info = ev.target.closest(".corp-info");
    if (info) {
      const id = Number(info.dataset.corp);
      corpOpenId = corpOpenId === id ? null : id;
      if (corpOpenId === id) collapsedBoardIds.delete(id);
      applyBoardUi();
      return;
    }
    const toggle = ev.target.closest(".board-toggle");
    if (!toggle) return;
    const id = Number(toggle.dataset.corp);
    if (collapsedBoardIds.has(id)) collapsedBoardIds.delete(id);
    else collapsedBoardIds.add(id);
    applyBoardUi();
  });
}

function bindScoreUi() {
  if (scoreUiBound) return;
  scoreUiBound = true;
  $("score-toggle").addEventListener("click", () => {
    scoreOpen = !scoreOpen;
    if (!scoreOpen) {
      cardsOpen = false;
      chartsOpen = false;
    }
    $("score-details").hidden = !scoreOpen;
    $("score-cards").hidden = !cardsOpen;
    $("score-charts").hidden = !chartsOpen;
    $("score-chart-toggle").setAttribute("aria-expanded", chartsOpen ? "true" : "false");
    $("score-toggle").setAttribute("aria-expanded", scoreOpen ? "true" : "false");
  });
  $("score-lines").addEventListener("click", (ev) => {
    const row = ev.target.closest("tr[data-drill='cards']");
    if (!row) return;
    cardsOpen = !cardsOpen;
    $("score-cards").hidden = !cardsOpen;
    row.classList.toggle("open", cardsOpen);
  });
  $("score-chart-toggle").addEventListener("click", () => {
    chartsOpen = !chartsOpen;
    $("score-charts").hidden = !chartsOpen;
    $("score-chart-toggle").setAttribute("aria-expanded", chartsOpen ? "true" : "false");
  });
  $("score-charts").addEventListener("click", (ev) => {
    const col = ev.target.closest("[data-gen]");
    if (!col) return;
    const gen = Number(col.dataset.gen);
    selectedGen = selectedGen === gen ? null : gen;
    $("score-charts").querySelectorAll("[data-gen]").forEach((el) => {
      el.classList.toggle("selected", Number(el.dataset.gen) === selectedGen);
    });
    const history = lastScoreData?.score?.history || [];
    renderGenExplain(lastScoreData || {}, lastScorePlayers, history);
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
bindCorpUi();
