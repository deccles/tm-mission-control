const fs = require("fs");
const path = require("path");
const https = require("https");

const HTML_URL = "https://raw.githubusercontent.com/ssimeonoff/ssimeonoff.github.io/master/cards-list.html";
const htmlCandidates = [
  path.join(process.env.TEMP || "/tmp", "tm-cards-list.html"),
  path.join(__dirname, "ssimeonoff-cards-list.html"),
];
const outFile = path.join(__dirname, "expansions.json");

const TAGS = ["building", "space", "science", "plant", "microbe", "animal", "power", "jovian", "earth", "city", "event", "venus", "wild"];

const CORPS = [
  { key: "Aphrodite", name: "Aphrodite", number: "Z23", tags: ["venus", "plant"], resources: { mc: 47 }, production: { plant: 1 }, extra: "Effect: Whenever Venus is terraformed 1 step, you gain 2 MC." },
  { key: "Celestic", name: "Celestic", number: "Z24", tags: ["venus"], resources: { mc: 42 }, production: {}, extra: "Action: Add a floater to ANY card.\n(1 VP per 3 floaters on this card.)\nVP: 1/3 Floater Resource" },
  { key: "Manutech", name: "Manutech", number: "Z25", tags: ["building"], resources: { mc: 35 }, production: { steel: 1 }, extra: "Effect: For each step you increase the production of a resource, including this, also gain that resource." },
  { key: "MorningStarInc", name: "Morning Star Inc", number: "Z26", tags: ["venus"], resources: { mc: 50 }, production: {}, extra: "You start with 50 MC. As your first action, reveal cards from the deck until you have revealed 3 Venus-tag cards. Take those into hand and discard the rest.\nEffect: Your Venus requirements are +2 or -2 steps, your choice in each case." },
  { key: "Viron", name: "Viron", number: "Z27", tags: [], resources: { mc: 48 }, production: {}, extra: "Action: Use a blue card action that has already been used this generation." },
  { key: "Arklight", name: "Arklight", number: "Z28", tags: ["animal"], resources: { mc: 45 }, production: { mc: 2 }, extra: "Effect: When you play an animal or plant tag, including this, add 1 animal to this card.\n(1 VP per 2 animals on this card.)\nVP: 1/2 Animal Resource" },
  { key: "Aridor", name: "Aridor", number: "Z29", tags: [], resources: { mc: 40 }, production: {}, extra: "As your first action, put an additional colony tile of your choice into play. Effect: When you get a new type of tag in play (event cards do not count), increase your MC production 1 step." },
  { key: "Polyphemos", name: "Polyphemos", number: "Z30", tags: [], resources: { mc: 50, ti: 5 }, production: { mc: 5 }, extra: "You start with 50 MC, 5 titanium, and 5 MC production.\nEffect: When you buy a card to hand, pay 5 MC instead of 3, including the starting hand." },
  { key: "Poseidon", name: "Poseidon", number: "Z31", tags: [], resources: { mc: 45 }, production: {}, extra: "You start with 45 MC. As your first action, place a colony.\nEffect: When you add a colony, raise your MC production 1 step." },
  { key: "StormcraftIncorporated", name: "Stormcraft Incorporated", number: "Z32", tags: [], resources: { mc: 48 }, production: {}, extra: "Action: Add a floater to ANY card.\nEffect: Floaters on this card may be used as 2 heat each." },
  { key: "Splice", name: "Splice", number: "Z33", tags: ["microbe", "science"], resources: { mc: 44 }, production: {}, extra: "Effect: when you or an opponent plays a microbe tag, including this, that player gains 2 MC or adds a microbe to THAT card, and you gain 2 MC." },
  { key: "Recyclon", name: "Recyclon", number: "Z34", tags: ["building", "microbe"], resources: { mc: 38 }, production: { steel: 1 }, extra: "You start with 38 MC and 1 steel production. Add 2 microbes to this card.\nEffect: When you play a building tag, including this, add a microbe here or gain 2 MC." },
  { key: "ArcadianCommunities", name: "Arcadian Communities", number: "Z35", tags: [], resources: { mc: 40, steel: 10 }, production: {}, extra: "You start with 40 MC and 10 steel. Place a special tile. Action: If you own that special tile and it is adjacent to a tile you own, you may claim it (gain the placement bonus)." },
  { key: "PharmacyUnion", name: "Pharmacy Union", number: "Z36", tags: ["science", "science"], resources: { mc: 54 }, production: {}, extra: "You start with 54 MC. Draw a science card.\nEffect: When you play a science tag, including this, gain 1 MC per science tag you have, including this.\nEffect: When you play a microbe tag, lose 4 MC and a science tag (remove a disease)." },
  { key: "Astrodrill", name: "Astrodrill", number: "Z37", tags: ["space"], resources: { mc: 38 }, production: {}, extra: "You start with 38 MC and 3 asteroid resources on this card.\nAction: Spend 1 asteroid resource here to gain 3 titanium, OR gain 1 asteroid resource, OR spend 1 asteroid resource to draw a card." },
];

function fetchUrl(url) {
  return new Promise((resolve, reject) => {
    https.get(url, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        fetchUrl(res.headers.location).then(resolve, reject);
        return;
      }
      if (res.statusCode !== 200) {
        reject(new Error("HTTP " + res.statusCode));
        return;
      }
      const chunks = [];
      res.on("data", (c) => chunks.push(c));
      res.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    }).on("error", reject);
  });
}

function toKey(title) {
  const words = title
    .replace(/['’]/g, "")
    .replace(/[^A-Za-z0-9]+/g, " ")
    .trim()
    .split(/\s+/);
  return words.map((w) => {
    const u = w.toUpperCase();
    if (["GHG", "CEO", "AI", "UNMI", "L1"].includes(u)) return u;
    return w.charAt(0).toUpperCase() + w.slice(1);
  }).join("");
}

function textOf(html) {
  return html
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&#x25CF;/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

function descriptions(block) {
  const out = [];
  const re = /<div class="description"[^>]*>([\s\S]*?)<\/div>/gi;
  let m;
  while ((m = re.exec(block))) {
    const t = textOf(m[1]);
    if (t) out.push(t);
  }
  return out;
}

function keepProject(cls, num) {
  if (/\b(global-card|colony-card|prelude2)\b/.test(cls)) return false;
  if (/\bcorporation\b/.test(cls)) return false;
  if (/\bturmoil\b/.test(cls) && !/\bpromo\b/.test(cls)) return false;
  if (/\bvenusNext\b/.test(cls)) return true;
  if (/^C\d+$/i.test(num)) return true;
  if (/^X\d+$/i.test(num)) return true;
  const n = parseInt(num, 10);
  if (n >= 209 && n <= 261) return true;
  if (/\bpromo\b/.test(cls) && /\b(automated|active|events)\b/.test(cls)) return true;
  return false;
}

function colorOf(cls) {
  if (/\bevents\b/.test(cls)) return "red";
  if (/\bactive\b/.test(cls)) return "blue";
  if (/\bprelude-card\b/.test(cls) || /\bprelude\b/.test(cls)) return "green";
  return "green";
}

function typeOf(cls) {
  if (/\bprelude-card\b/.test(cls)) return "prel";
  return "proj";
}

function tagsOf(cls, block) {
  const tags = [];
  for (const tag of TAGS) {
    if (new RegExp("\\btag-" + tag + "\\b").test(block) || new RegExp("\\b" + tag + "Tag\\b").test(cls)) {
      tags.push(tag);
    }
  }
  if (/\bevents\b/.test(cls) && !tags.includes("event")) tags.push("event");
  return [...new Set(tags)];
}

function placeOf(extra) {
  const place = [];
  const extraLower = extra.toLowerCase();
  if (/special tile/.test(extraLower)) place.push("special tile");
  if (/place a city/.test(extraLower)) place.push("city");
  if (/place a greenery/.test(extraLower)) place.push("greenery");
  if (/place 1 ocean|place an ocean|ocean tile/.test(extraLower)) place.push("ocean");
  return place;
}

function parseVp(block, extra) {
  const big = block.match(/<div class="points points-big">\s*([+-]?\d+)/);
  if (big) return { vp: Number(big[1]), extra };
  const pts = block.match(/<div class="points"[^>]*>([\s\S]*?)<\/div>/);
  if (!pts) return { vp: null, extra };
  const inner = pts[1];
  const num = textOf(inner.replace(/<div[\s\S]*$/, ""));
  if (/^\d+$/.test(num)) return { vp: Number(num), extra };
  if (inner.includes("animal")) extra += (extra ? "\n" : "") + "VP: 1/Animal Resource";
  else if (inner.includes("microbe")) extra += (extra ? "\n" : "") + "VP: 1/Microbe Resource";
  else if (inner.includes("floater")) extra += (extra ? "\n" : "") + "VP: 1/Floater Resource";
  else if (inner.includes("jovian") || inner.includes("tag-jovian")) extra += (extra ? "\n" : "") + "VP: 1/Jovian";
  else if (inner.includes("venus") || inner.includes("tag-venus")) extra += (extra ? "\n" : "") + "VP: 1/Venus";
  else if (inner.includes("science")) extra += (extra ? "\n" : "") + "VP: 1/Science Resource";
  return { vp: null, extra };
}

function parseProjects(html) {
  const cards = [];
  const seen = new Set();
  const parts = html.split(/<li\b/i).slice(1);
  for (const part of parts) {
    const end = part.indexOf("</li>");
    const block = part.slice(0, end < 0 ? part.length : end);
    const cls = (block.match(/class="([^"]*)"/) || [])[1] || "";
    const title = textOf((block.match(/<div class="title[^"]*">([\s\S]*?)<\/div>/) || [])[1] || "");
    const numRaw = textOf((block.match(/<div class="number">([\s\S]*?)<\/div>/) || [])[1] || "").replace(/^#/, "");
    if (!title || !numRaw) continue;
    if (!keepProject(cls, numRaw)) continue;
    const key = toKey(title);
    if (!key || seen.has(key)) continue;
    seen.add(key);
    let extra = descriptions(block).join(" ");
    const leftover = block.match(/<div class="content">([\s\S]*?)<\/div>\s*$/);
    if (!extra && leftover) {
      extra = textOf(leftover[1]);
    }
    const { vp, extra: extra2 } = parseVp(block, extra);
    extra = extra2;
    const price = textOf((block.match(/<div class="price">([\s\S]*?)<\/div>/) || [])[1] || "");
    cards.push({
      key,
      name: title.replace(/\s+/g, " ").trim(),
      number: numRaw,
      type: typeOf(cls),
      color: colorOf(cls),
      cost: price === "" ? null : Number(price),
      tags: tagsOf(cls, block),
      vp,
      resources: {},
      production: {},
      extra,
      place: placeOf(extra),
      req: {},
    });
  }
  return cards;
}

function corpCards() {
  return CORPS.map((c) => ({
    key: c.key,
    name: c.name,
    number: c.number,
    type: "corp",
    color: "blue",
    cost: null,
    tags: c.tags,
    vp: null,
    resources: c.resources,
    production: c.production,
    extra: c.extra,
    place: [],
    req: {},
  }));
}

async function loadHtml() {
  for (const p of htmlCandidates) {
    if (fs.existsSync(p)) {
      console.log("Reading", p);
      return fs.readFileSync(p, "utf8");
    }
  }
  console.log("Fetching", HTML_URL);
  return fetchUrl(HTML_URL);
}

async function main() {
  const html = await loadHtml();
  const projects = parseProjects(html);
  const cards = [...corpCards(), ...projects];
  fs.writeFileSync(outFile, JSON.stringify(cards, null, 2));
  const venus = projects.filter((c) => /^\d+$/.test(c.number) && Number(c.number) >= 213 && Number(c.number) <= 261).length;
  const colonies = projects.filter((c) => /^C/i.test(c.number)).length;
  const promo = projects.length - venus - colonies;
  console.log("Wrote", cards.length, "cards to", outFile, { corps: CORPS.length, venus, colonies, promo });
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
