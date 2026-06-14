/* ===== Artha presentation — interactions ===== */
(() => {
  "use strict";
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* scroll progress + nav state */
  const progress = $("#progress");
  const nav = $("#nav");
  const onScroll = () => {
    const h = document.documentElement;
    const p = h.scrollTop / (h.scrollHeight - h.clientHeight || 1);
    if (progress) progress.style.width = (p * 100).toFixed(2) + "%";
    nav.classList.toggle("scrolled", h.scrollTop > 30);
  };
  addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  /* reveal on scroll */
  const revObs = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      if (e.isIntersecting) { e.target.classList.add("in"); revObs.unobserve(e.target); }
    });
  }, { threshold: 0.12, rootMargin: "0px 0px -8% 0px" });
  $$(".reveal").forEach((el) => revObs.observe(el));
  // hero reveals fire immediately on load
  requestAnimationFrame(() => $$(".hero .reveal").forEach((el) => el.classList.add("in")));

  /* active nav link */
  const linkFor = {};
  $$(".nav__links a").forEach((a) => (linkFor[a.getAttribute("href").slice(1)] = a));
  const secObs = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      const a = linkFor[e.target.id];
      if (a && e.isIntersecting) {
        $$(".nav__links a").forEach((x) => x.classList.remove("active"));
        a.classList.add("active");
      }
    });
  }, { rootMargin: "-45% 0px -50% 0px" });
  ["problem", "loop", "product", "edge", "trust"].forEach((id) => {
    const s = document.getElementById(id); if (s) secObs.observe(s);
  });

  /* count-up stats */
  const countObs = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      if (!e.isIntersecting) return;
      countObs.unobserve(e.target);
      const el = e.target;
      const to = parseFloat(el.dataset.to);
      const suf = el.dataset.suffix || "";
      const pre = el.dataset.prefix || "";
      if (!to) { el.textContent = el.dataset.zero || "0"; return; }
      const dur = 1500, t0 = performance.now();
      const tick = (t) => {
        const k = Math.min(1, (t - t0) / dur);
        const ease = 1 - Math.pow(1 - k, 3);
        const v = to >= 10 ? Math.round(to * ease) : (to * ease).toFixed(0);
        el.textContent = pre + v + suf;
        if (k < 1) requestAnimationFrame(tick);
      };
      reduce ? (el.textContent = pre + to + suf) : requestAnimationFrame(tick);
    });
  }, { threshold: 0.5 });
  $$(".stat__num").forEach((el) => countObs.observe(el));

  /* loop stepper */
  const navItems = $$("#loopNav li");
  const panes = $$("#loopPanes .loop__pane");
  let cur = 0, auto;
  const setStep = (i) => {
    cur = i;
    navItems.forEach((n, k) => n.classList.toggle("is-active", k === i));
    panes.forEach((p, k) => p.classList.toggle("is-active", k === i));
  };
  navItems.forEach((n) =>
    n.addEventListener("click", () => { setStep(+n.dataset.i); clearInterval(auto); })
  );
  const startAuto = () => {
    if (reduce) return;
    auto = setInterval(() => setStep((cur + 1) % panes.length), 4800);
  };
  // only auto-rotate while the loop section is on screen
  const loopSec = $("#loop");
  if (loopSec) new IntersectionObserver((es) => {
    es.forEach((e) => (e.isIntersecting ? startAuto() : clearInterval(auto)));
  }, { threshold: 0.25 }).observe(loopSec);

  /* hero phone 3D tilt */
  const stage = $(".hero__device"), tiltEl = $("[data-tilt]");
  if (stage && tiltEl && !reduce && matchMedia("(pointer:fine)").matches) {
    stage.addEventListener("mousemove", (ev) => {
      const r = stage.getBoundingClientRect();
      const x = (ev.clientX - r.left) / r.width - 0.5;
      const y = (ev.clientY - r.top) / r.height - 0.5;
      tiltEl.style.transform = `rotateY(${x * 10}deg) rotateX(${-y * 10}deg) translateY(${y * 6}px)`;
      tiltEl.style.animationPlayState = "paused";
    });
    stage.addEventListener("mouseleave", () => {
      tiltEl.style.transform = "";
      tiltEl.style.animationPlayState = "";
    });
  }
})();
