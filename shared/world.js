export const DEFAULT_SEED = 731942;
export const WORLD_RADIUS = 23;
export const MAX_REACH = 6.5;
export const PLAYER_HEIGHT = 1.76;
export const PLAYER_HALF_WIDTH = 0.29;

export const BLOCKS = Object.freeze({
  foundation: { name: '岛核', color: '#263541', accent: '#425563', unbreakable: true },
  humus: { name: '褐壤', color: '#88634e', accent: '#b48a67' },
  verdure: { name: '青绒', color: '#4f8b78', accent: '#8fc59a' },
  chalk: { name: '云砂', color: '#c7b99c', accent: '#eee0bd' },
  shale: { name: '层岩', color: '#657681', accent: '#91a4aa' },
  timber: { name: '纹木', color: '#ad7249', accent: '#e0a562' },
  leaf: { name: '冠叶', color: '#39776e', accent: '#70b69a' },
  glow: { name: '星珀', color: '#e8a44f', accent: '#ffe29a', emissive: '#d67b28' },
});

export const HOTBAR = ['verdure', 'humus', 'chalk', 'shale', 'timber', 'leaf', 'glow'];
export const STARTING_INVENTORY = Object.freeze({
  verdure: 8,
  humus: 0,
  chalk: 0,
  shale: 12,
  timber: 8,
  leaf: 0,
  glow: 2,
});

export function blockKey(x, y, z) {
  return `${x},${y},${z}`;
}

export function parseBlockKey(key) {
  return key.split(',').map(Number);
}

function hash2(x, z, seed) {
  let h = Math.imul(x, 374761393) + Math.imul(z, 668265263) + Math.imul(seed, 69069);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}

function smoothstep(t) {
  return t * t * (3 - 2 * t);
}

function noise2(x, z, seed, scale = 1) {
  const sx = x / scale;
  const sz = z / scale;
  const x0 = Math.floor(sx);
  const z0 = Math.floor(sz);
  const tx = smoothstep(sx - x0);
  const tz = smoothstep(sz - z0);
  const a = hash2(x0, z0, seed);
  const b = hash2(x0 + 1, z0, seed);
  const c = hash2(x0, z0 + 1, seed);
  const d = hash2(x0 + 1, z0 + 1, seed);
  const top = a + (b - a) * tx;
  const bottom = c + (d - c) * tx;
  return top + (bottom - top) * tz;
}

function surfaceHeight(x, z, seed) {
  const broad = noise2(x, z, seed, 11);
  const detail = noise2(x, z, seed + 97, 4);
  const distance = Math.hypot(x, z);
  return Math.max(1, Math.min(9, Math.floor(2.2 + broad * 4.4 + detail * 1.6 - Math.max(0, distance - 13) * 0.16)));
}

export function generateWorld(seed = DEFAULT_SEED) {
  const blocks = new Map();
  const surfaces = new Map();

  for (let x = -WORLD_RADIUS; x <= WORLD_RADIUS; x += 1) {
    for (let z = -WORLD_RADIUS; z <= WORLD_RADIUS; z += 1) {
      const angle = Math.atan2(z, x);
      const distance = Math.hypot(x, z);
      const raggedEdge = WORLD_RADIUS - 1.2 + Math.sin(angle * 5 + seed) * 1.2 + noise2(x, z, seed + 31, 7) * 2.2;
      if (distance > raggedEdge) continue;

      const top = surfaceHeight(x, z, seed);
      const depth = Math.max(2, Math.floor(2 + (1 - distance / WORLD_RADIUS) * 4 + noise2(x, z, seed + 53, 6) * 2));
      const bottom = top - depth;
      surfaces.set(`${x},${z}`, top);

      for (let y = bottom; y <= top; y += 1) {
        let type = 'shale';
        if (y === bottom) type = 'foundation';
        else if (y === top) type = distance > WORLD_RADIUS - 5 && hash2(x, z, seed + 11) > 0.36 ? 'chalk' : 'verdure';
        else if (y >= top - 2) type = 'humus';
        blocks.set(blockKey(x, y, z), type);
      }
    }
  }

  // Umbrella-like copperwood trees and star-amber clusters give the island its own silhouette.
  for (let x = -WORLD_RADIUS + 2; x <= WORLD_RADIUS - 2; x += 1) {
    for (let z = -WORLD_RADIUS + 2; z <= WORLD_RADIUS - 2; z += 1) {
      const top = surfaces.get(`${x},${z}`);
      if (top === undefined || Math.hypot(x, z) < 4.5) continue;
      const surfaceType = blocks.get(blockKey(x, top, z));
      const chance = hash2(x, z, seed + 401);

      if (surfaceType === 'verdure' && chance > 0.974) {
        const trunkHeight = 3 + Math.floor(hash2(x, z, seed + 402) * 2);
        for (let y = 1; y <= trunkHeight; y += 1) blocks.set(blockKey(x, top + y, z), 'timber');
        const crownY = top + trunkHeight;
        for (let dx = -2; dx <= 2; dx += 1) {
          for (let dz = -2; dz <= 2; dz += 1) {
            if (Math.abs(dx) + Math.abs(dz) <= 3 && !(dx === 0 && dz === 0)) {
              blocks.set(blockKey(x + dx, crownY, z + dz), 'leaf');
            }
          }
        }
        blocks.set(blockKey(x, crownY + 1, z), 'leaf');
        blocks.set(blockKey(x + 1, crownY + 1, z), 'leaf');
        blocks.set(blockKey(x - 1, crownY + 1, z), 'leaf');
        blocks.set(blockKey(x, crownY + 1, z + 1), 'leaf');
        blocks.set(blockKey(x, crownY + 1, z - 1), 'leaf');
      } else if (chance > 0.958) {
        blocks.set(blockKey(x, top + 1, z), 'glow');
        if (hash2(x, z, seed + 403) > 0.63) blocks.set(blockKey(x, top + 2, z), 'glow');
      }
    }
  }

  return blocks;
}

export class VoxelWorld {
  constructor(seed = DEFAULT_SEED, edits = []) {
    this.seed = seed;
    this.base = generateWorld(seed);
    this.blocks = new Map(this.base);
    this.edits = new Map();
    for (const edit of edits) {
      if (!Array.isArray(edit) || edit.length !== 4) continue;
      const [x, y, z, type] = edit;
      if (![x, y, z].every(Number.isInteger)) continue;
      if (type !== null && !BLOCKS[type]) continue;
      this.setBlock(x, y, z, type);
    }
  }

  getBlock(x, y, z) {
    return this.blocks.get(blockKey(x, y, z)) ?? null;
  }

  setBlock(x, y, z, type) {
    const key = blockKey(x, y, z);
    const previous = this.blocks.get(key) ?? null;
    if (previous === type) return false;
    if (type === null) this.blocks.delete(key);
    else this.blocks.set(key, type);

    const original = this.base.get(key) ?? null;
    if (original === type) this.edits.delete(key);
    else this.edits.set(key, type);
    return true;
  }

  serializeEdits() {
    return [...this.edits].map(([key, type]) => [...parseBlockKey(key), type]);
  }

  highestBlock(x, z) {
    for (let y = 36; y >= -12; y -= 1) {
      if (this.getBlock(x, y, z)) return y;
    }
    return -12;
  }
}

export function inBuildBounds(x, y, z) {
  return Number.isInteger(x) && Number.isInteger(y) && Number.isInteger(z)
    && Math.abs(x) <= 40 && Math.abs(z) <= 40 && y >= -10 && y <= 32;
}

export function raycastVoxel(world, origin, direction, maxDistance = MAX_REACH) {
  const length = Math.hypot(direction.x, direction.y, direction.z);
  if (!Number.isFinite(length) || length < 0.00001) return null;
  const dx = direction.x / length;
  const dy = direction.y / length;
  const dz = direction.z / length;
  let x = Math.floor(origin.x);
  let y = Math.floor(origin.y);
  let z = Math.floor(origin.z);
  const stepX = Math.sign(dx);
  const stepY = Math.sign(dy);
  const stepZ = Math.sign(dz);
  const deltaX = stepX === 0 ? Infinity : Math.abs(1 / dx);
  const deltaY = stepY === 0 ? Infinity : Math.abs(1 / dy);
  const deltaZ = stepZ === 0 ? Infinity : Math.abs(1 / dz);
  let maxX = stepX === 0 ? Infinity : ((stepX > 0 ? x + 1 - origin.x : origin.x - x) * deltaX);
  let maxY = stepY === 0 ? Infinity : ((stepY > 0 ? y + 1 - origin.y : origin.y - y) * deltaY);
  let maxZ = stepZ === 0 ? Infinity : ((stepZ > 0 ? z + 1 - origin.z : origin.z - z) * deltaZ);
  let distance = 0;
  let normal = { x: 0, y: 0, z: 0 };

  while (distance <= maxDistance) {
    const type = world.getBlock(x, y, z);
    if (type) return { x, y, z, type, normal, distance };
    if (maxX <= maxY && maxX <= maxZ) {
      x += stepX;
      distance = maxX;
      maxX += deltaX;
      normal = { x: -stepX, y: 0, z: 0 };
    } else if (maxY <= maxZ) {
      y += stepY;
      distance = maxY;
      maxY += deltaY;
      normal = { x: 0, y: -stepY, z: 0 };
    } else {
      z += stepZ;
      distance = maxZ;
      maxZ += deltaZ;
      normal = { x: 0, y: 0, z: -stepZ };
    }
  }
  return null;
}

export function playerIntersectsBlock(position, x, y, z) {
  return position.x + PLAYER_HALF_WIDTH > x && position.x - PLAYER_HALF_WIDTH < x + 1
    && position.y + PLAYER_HEIGHT > y && position.y < y + 1
    && position.z + PLAYER_HALF_WIDTH > z && position.z - PLAYER_HALF_WIDTH < z + 1;
}

export function findSpawn(world) {
  for (let radius = 0; radius < 9; radius += 1) {
    for (let x = -radius; x <= radius; x += 1) {
      for (let z = -radius; z <= radius; z += 1) {
        if (radius > 0 && Math.abs(x) !== radius && Math.abs(z) !== radius) continue;
        const y = world.highestBlock(x, z);
        const floorType = world.getBlock(x, y, z);
        if ((floorType === 'verdure' || floorType === 'humus' || floorType === 'chalk')
          && !world.getBlock(x, y + 1, z) && !world.getBlock(x, y + 2, z)) {
          return { x: x + 0.5, y: y + 1.002, z: z + 0.5 };
        }
      }
    }
  }
  return { x: 0.5, y: 12, z: 0.5 };
}
