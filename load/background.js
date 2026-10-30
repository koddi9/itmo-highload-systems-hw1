// Разгон до TARGET_RPS и удержание до остановки стека.
// Одна итерация - один запрос, поэтому arrival rate равен суммарному RPS.
import http from 'k6/http';

const TARGETS = (__ENV.TARGETS || 'http://localhost:8081,http://localhost:8082')
  .split(',').map((url) => url.trim()).filter(Boolean);
const TARGET_RPS = Number(__ENV.TARGET_RPS || 40);
const RAMP_UP = __ENV.RAMP_UP || '3m';
if (!(TARGET_RPS > 0 && TARGET_RPS <= 200)) {
  throw new Error('TARGET_RPS must be within (0, 200]');
}

// Доли запросов, остальное - PUT.
const CREATE_SHARE = 0.30;
const INVALID_CREATE_SHARE = 0.01; // пустой name: 400
const READ_SHARE = 0.46;
// Доля GET и PUT по несуществующим id: 404. 500 добавляет сам сервис (FAILURE_RATE).
const MISSING_ID_SHARE = 0.05;
const SEED_ITEMS = 10;
const KNOWN_IDS_LIMIT = 500;

export const options = {
  scenarios: {
    background: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 10,
      maxVUs: 100,
      stages: [
        { target: TARGET_RPS, duration: RAMP_UP },
        // Год, то есть до docker compose down.
        { target: TARGET_RPS, duration: '8760h' },
      ],
    },
  },
};

// У каждого инстанса своё хранилище, поэтому id храним отдельно для каждого.
export function setup() {
  const seeded = {};
  for (const base of TARGETS) {
    seeded[base] = [];
    for (let i = 0; i < SEED_ITEMS; i++) {
      const res = createItem(base, `seed-${i}`);
      if (res.status === 201) {
        seeded[base].push(res.json('id'));
      }
    }
  }
  return seeded;
}

// Модуль исполняется в каждом VU отдельно, у каждого VU своя копия id.
const knownIds = {};

export default function (seeded) {
  const base = TARGETS[Math.floor(Math.random() * TARGETS.length)];
  if (!knownIds[base]) {
    knownIds[base] = seeded[base].slice();
  }
  const ids = knownIds[base];
  const roll = Math.random();

  if (roll < CREATE_SHARE) {
    const res = createItem(base, `item-${__VU}-${__ITER}`);
    if (res.status === 201) {
      remember(ids, res.json('id'));
    }
  } else if (roll < CREATE_SHARE + INVALID_CREATE_SHARE) {
    http.post(`${base}/items`, JSON.stringify({ name: '' }), params('POST /items'));
  } else if (roll < CREATE_SHARE + INVALID_CREATE_SHARE + READ_SHARE) {
    http.get(`${base}/items/${pickId(ids)}`, params('GET /items/{id}'));
  } else {
    const body = JSON.stringify({ name: `item-${__VU}-${__ITER}`, description: 'updated by k6' });
    http.put(`${base}/items/${pickId(ids)}`, body, params('PUT /items/{id}'));
  }
}

function createItem(base, name) {
  const body = JSON.stringify({ name, description: 'created by k6' });
  return http.post(`${base}/items`, body, params('POST /items'));
}

function pickId(ids) {
  if (ids.length === 0 || Math.random() < MISSING_ID_SHARE) {
    return 1000000000 + Math.floor(Math.random() * 1000000);
  }
  return ids[Math.floor(Math.random() * ids.length)];
}

function remember(ids, id) {
  ids.push(id);
  if (ids.length > KNOWN_IDS_LIMIT) {
    ids.shift();
  }
}

// Тег name группирует метрики k6 по ручке, а не по каждому URL.
function params(name) {
  return { headers: { 'Content-Type': 'application/json' }, tags: { name }, timeout: '5s' };
}
