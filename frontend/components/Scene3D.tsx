"use client";

import { useEffect, useRef } from "react";

type Three = typeof import("three");

const PINK = 0xff4fa3;
const CYAN = 0x22e1ff;
const LIME = 0xc6ff3d;
const VIOLET = 0x9a6bff;
const ORANGE = 0xff9f2e;

/**
 * A WebGL scene behind sign-in and register: a faceted crystal "knowledge
 * core" lit by orbiting colored lights, a wireframe shell, tilted glowing
 * rings, document pages orbiting on their own paths, and a field of stars.
 * The pointer tilts the camera a little.
 *
 * `focus` slides the whole scene left (-1), to the middle (0) or right (1),
 * easing towards it every frame, so it can sit opposite the form card.
 * `active` pauses rendering entirely while the scene is hidden.
 *
 * three.js is loaded on demand, so the welcome page does not wait for it.
 */
export default function Scene3D({
  focus,
  active,
}: {
  focus: number;
  active: boolean;
}) {
  const hostRef = useRef<HTMLDivElement>(null);
  // The render loop reads these on every frame instead of being rebuilt.
  const focusRef = useRef(focus);
  const activeRef = useRef(active);

  useEffect(() => {
    focusRef.current = focus;
    activeRef.current = active;
  }, [focus, active]);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;

    let disposed = false;
    let cleanup = () => {};

    import("three").then((THREE) => {
      if (disposed) return;
      cleanup = buildScene(THREE, host, focusRef, activeRef);
    });

    return () => {
      disposed = true;
      cleanup();
    };
  }, []);

  return <div ref={hostRef} aria-hidden className="absolute inset-0" />;
}

function buildScene(
  THREE: Three,
  host: HTMLDivElement,
  focusRef: React.RefObject<number>,
  activeRef: React.RefObject<boolean>,
): () => void {
  const reducedMotion = window.matchMedia(
    "(prefers-reduced-motion: reduce)",
  ).matches;

  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.75));
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.setClearColor(0x000000, 0);
  host.appendChild(renderer.domElement);
  renderer.domElement.style.display = "block";

  const scene = new THREE.Scene();
  scene.fog = new THREE.FogExp2(0x0a0516, 0.045);

  const camera = new THREE.PerspectiveCamera(45, 1, 0.1, 100);
  camera.position.set(0, 0, 11);

  // Everything that moves with `focus` lives in this group.
  const world = new THREE.Group();
  scene.add(world);

  // ---- Light: a soft fill plus colored lights circling the core ----------
  scene.add(new THREE.AmbientLight(0x6a4fb0, 0.6));
  const orbitLights = [PINK, CYAN, LIME, VIOLET].map((color, index) => {
    const light = new THREE.PointLight(color, 60, 18, 1.6);
    light.userData.phase = (index / 4) * Math.PI * 2;
    world.add(light);
    return light;
  });

  // ---- The core: a faceted crystal --------------------------------------
  const coreGeometry = new THREE.IcosahedronGeometry(1.35, 1);
  const coreMaterial = new THREE.MeshPhysicalMaterial({
    color: 0xffffff,
    metalness: 0.35,
    roughness: 0.18,
    clearcoat: 1,
    clearcoatRoughness: 0.1,
    iridescence: 1,
    iridescenceIOR: 1.4,
    flatShading: true,
  });
  const core = new THREE.Mesh(coreGeometry, coreMaterial);
  world.add(core);

  // A glowing heart inside the crystal.
  const heartGeometry = new THREE.SphereGeometry(0.55, 32, 32);
  const heartMaterial = new THREE.MeshBasicMaterial({
    color: LIME,
    transparent: true,
    opacity: 0.85,
  });
  const heart = new THREE.Mesh(heartGeometry, heartMaterial);
  world.add(heart);

  // ---- Wireframe shell ----------------------------------------------------
  const shellGeometry = new THREE.EdgesGeometry(
    new THREE.IcosahedronGeometry(2.15, 1),
  );
  const shellMaterial = new THREE.LineBasicMaterial({
    color: CYAN,
    transparent: true,
    opacity: 0.45,
  });
  const shell = new THREE.LineSegments(shellGeometry, shellMaterial);
  world.add(shell);

  // ---- Tilted glowing rings ----------------------------------------------
  const ringSpecs = [
    { radius: 2.9, color: PINK, tilt: [1.2, 0.3, 0] },
    { radius: 3.5, color: LIME, tilt: [0.4, -0.9, 0.2] },
    { radius: 4.2, color: VIOLET, tilt: [-0.7, 0.5, 0.6] },
  ] as const;
  const rings = ringSpecs.map((spec) => {
    const ring = new THREE.Mesh(
      new THREE.TorusGeometry(spec.radius, 0.025, 12, 180),
      new THREE.MeshBasicMaterial({
        color: spec.color,
        transparent: true,
        opacity: 0.8,
      }),
    );
    ring.rotation.set(spec.tilt[0], spec.tilt[1], spec.tilt[2]);
    world.add(ring);
    return ring;
  });

  // ---- Document pages orbiting the core ----------------------------------
  const pageTexture = new THREE.CanvasTexture(drawPage());
  pageTexture.colorSpace = THREE.SRGBColorSpace;
  const pageGeometry = new THREE.PlaneGeometry(0.62, 0.82);
  const pageColors = [PINK, CYAN, LIME, ORANGE, VIOLET];
  const pages = Array.from({ length: 11 }, (_, index) => {
    const material = new THREE.MeshStandardMaterial({
      map: pageTexture,
      emissive: pageColors[index % pageColors.length],
      emissiveIntensity: 0.35,
      roughness: 0.4,
      metalness: 0.1,
      side: THREE.DoubleSide,
      transparent: true,
      opacity: 0.95,
    });
    const page = new THREE.Mesh(pageGeometry, material);
    page.userData = {
      radius: 3 + (index % 4) * 0.55,
      speed: 0.12 + (index % 5) * 0.035,
      phase: (index / 11) * Math.PI * 2,
      tiltX: ((index * 37) % 100) / 100 - 0.5,
      tiltZ: ((index * 61) % 100) / 100 - 0.5,
      spin: 0.3 + (index % 3) * 0.25,
    };
    world.add(page);
    return page;
  });

  // ---- Stars ----------------------------------------------------------------
  const starCount = 1600;
  const starPositions = new Float32Array(starCount * 3);
  const starColors = new Float32Array(starCount * 3);
  const palette = [PINK, CYAN, LIME, VIOLET, 0xffffff].map(
    (hex) => new THREE.Color(hex),
  );
  for (let i = 0; i < starCount; i++) {
    // Spread through a thick spherical shell around the core.
    const radius = 5 + Math.random() * 14;
    const theta = Math.random() * Math.PI * 2;
    const phi = Math.acos(2 * Math.random() - 1);
    starPositions[i * 3] = radius * Math.sin(phi) * Math.cos(theta);
    starPositions[i * 3 + 1] = radius * Math.sin(phi) * Math.sin(theta);
    starPositions[i * 3 + 2] = radius * Math.cos(phi);
    const color = palette[i % palette.length];
    starColors[i * 3] = color.r;
    starColors[i * 3 + 1] = color.g;
    starColors[i * 3 + 2] = color.b;
  }
  const starGeometry = new THREE.BufferGeometry();
  starGeometry.setAttribute(
    "position",
    new THREE.BufferAttribute(starPositions, 3),
  );
  starGeometry.setAttribute("color", new THREE.BufferAttribute(starColors, 3));
  const starMaterial = new THREE.PointsMaterial({
    size: 0.06,
    vertexColors: true,
    transparent: true,
    opacity: 0.9,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  });
  const stars = new THREE.Points(starGeometry, starMaterial);
  scene.add(stars);

  // ---- Sizing -------------------------------------------------------------
  let halfWidth = 1;
  const resize = () => {
    const width = host.clientWidth || window.innerWidth;
    const height = host.clientHeight || window.innerHeight;
    renderer.setSize(width, height, false);
    renderer.domElement.style.width = "100%";
    renderer.domElement.style.height = "100%";
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
    // Half the visible width at the core's depth, used to place the scene in
    // the middle of the left or right half of the screen.
    halfWidth =
      Math.tan(THREE.MathUtils.degToRad(camera.fov / 2)) *
      camera.position.z *
      camera.aspect;
    // On narrow screens the card covers the middle, so shrink the scene a
    // little and keep it centered.
    world.scale.setScalar(width < 1024 ? 0.75 : 1);
  };
  const observer = new ResizeObserver(resize);
  observer.observe(host);
  resize();

  // ---- Pointer parallax ---------------------------------------------------
  const pointer = { x: 0, y: 0 };
  const onPointer = (event: PointerEvent) => {
    pointer.x = (event.clientX / window.innerWidth) * 2 - 1;
    pointer.y = (event.clientY / window.innerHeight) * 2 - 1;
  };
  window.addEventListener("pointermove", onPointer);

  // ---- Loop -----------------------------------------------------------------
  const clock = new THREE.Clock();
  let frame = 0;

  const tick = () => {
    frame = requestAnimationFrame(tick);
    if (!activeRef.current) {
      clock.getDelta(); // Don't let time pile up while paused.
      return;
    }

    const delta = Math.min(clock.getDelta(), 0.05);
    const time = clock.elapsedTime;
    const speed = reducedMotion ? 0 : 1;

    // Ease towards the side opposite the card.
    const wide = host.clientWidth >= 1024;
    const targetX = wide ? focusRef.current * halfWidth * 0.5 : 0;
    world.position.x += (targetX - world.position.x) * Math.min(delta * 2.6, 1);

    core.rotation.x += delta * 0.18 * speed;
    core.rotation.y += delta * 0.3 * speed;
    shell.rotation.x -= delta * 0.08 * speed;
    shell.rotation.y -= delta * 0.12 * speed;
    heart.scale.setScalar(1 + Math.sin(time * 2.2) * 0.08 * speed);

    rings.forEach((ring, index) => {
      ring.rotation.z += delta * (0.15 + index * 0.07) * speed * (index % 2 ? -1 : 1);
    });

    orbitLights.forEach((light, index) => {
      const angle = time * 0.6 * speed + light.userData.phase;
      light.position.set(
        Math.cos(angle) * 3.2,
        Math.sin(angle * 1.3 + index) * 1.8,
        Math.sin(angle) * 3.2,
      );
    });

    pages.forEach((page) => {
      const data = page.userData;
      const angle = time * data.speed * speed + data.phase;
      page.position.set(
        Math.cos(angle) * data.radius,
        Math.sin(angle) * data.radius * data.tiltX,
        Math.sin(angle) * data.radius + Math.cos(angle) * data.tiltZ,
      );
      page.rotation.y = angle * data.spin;
      page.rotation.x = Math.sin(angle) * 0.4;
    });

    stars.rotation.y += delta * 0.02 * speed;
    stars.rotation.x += delta * 0.006 * speed;

    camera.position.x += (pointer.x * 0.8 - camera.position.x) * 0.04;
    camera.position.y += (-pointer.y * 0.6 - camera.position.y) * 0.04;
    camera.lookAt(world.position.x * 0.35, 0, 0);

    renderer.render(scene, camera);
  };
  tick();

  return () => {
    cancelAnimationFrame(frame);
    observer.disconnect();
    window.removeEventListener("pointermove", onPointer);
    scene.traverse((object) => {
      const mesh = object as Disposable;
      mesh.geometry?.dispose();
      const material = mesh.material;
      if (Array.isArray(material)) material.forEach((entry) => entry.dispose());
      else material?.dispose();
    });
    pageTexture.dispose();
    renderer.dispose();
    renderer.domElement.remove();
  };
}

type Disposable = {
  geometry?: { dispose(): void };
  material?: { dispose(): void } | { dispose(): void }[];
};

/** A small page: colored title bar and lines of "text", drawn once. */
function drawPage(): HTMLCanvasElement {
  const canvas = document.createElement("canvas");
  canvas.width = 124;
  canvas.height = 164;
  const context = canvas.getContext("2d");
  if (!context) return canvas;

  context.fillStyle = "#fff8f0";
  context.beginPath();
  context.roundRect(0, 0, 124, 164, 10);
  context.fill();

  context.fillStyle = "#ff4fa3";
  context.fillRect(12, 14, 64, 10);

  context.fillStyle = "#b9aed6";
  for (let line = 0; line < 9; line++) {
    const width = line % 3 === 2 ? 60 : 100;
    context.fillRect(12, 38 + line * 13, width, 5);
  }

  context.fillStyle = "#22e1ff";
  context.fillRect(12, 148, 34, 6);
  return canvas;
}
