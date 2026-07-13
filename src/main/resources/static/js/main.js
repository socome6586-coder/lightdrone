/* ===========================
   Phone auto-format (010-1234-5678)
=========================== */
(function () {
    function formatPhone(input) {
        let d = input.value.replace(/[^0-9]/g, '');
        if (d.length > 11) d = d.slice(0, 11);
        if (d.length <= 3)      input.value = d;
        else if (d.length <= 7) input.value = d.slice(0,3) + '-' + d.slice(3);
        else                    input.value = d.slice(0,3) + '-' + d.slice(3,7) + '-' + d.slice(7);
    }
    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('.phone-input').forEach(function (el) {
            if (el.value) formatPhone(el);
            el.addEventListener('input', function () { formatPhone(this); });
        });
        document.querySelectorAll('form').forEach(function (form) {
            form.addEventListener('submit', function () {
                form.querySelectorAll('.phone-input').forEach(function (el) {
                    el.value = el.value.replace(/-/g, '');
                });
            }, true);
        });
    });
})();

/* ===========================
   Active menu link highlight
=========================== */
(function () {
    const path = window.location.pathname;
    const params = new URLSearchParams(window.location.search);
    const currentCategory = params.get('category');

    document.querySelectorAll('.menu-link').forEach(link => {
        link.classList.remove('active');

        const href = link.getAttribute('href');
        if (!href) return;

        try {
            const url = new URL(href, window.location.origin);
            const linkPath = url.pathname;
            const linkCategory = url.searchParams.get('category');

            if (path === '/products') {
                if (!currentCategory && linkPath === '/products' && !linkCategory) {
                    link.classList.add('active');
                } else if (currentCategory && linkCategory === currentCategory) {
                    link.classList.add('active');
                }
                return;
            }

            if (path === '/' && linkPath === '/') {
                link.classList.add('active');
                return;
            }

            if (path === linkPath && linkPath !== '/products') {
                link.classList.add('active');
            }
        } catch (e) {
            if (href === '/' && path === '/') {
                link.classList.add('active');
            }
        }
    });
})();

/* ===========================
   Header shadow on scroll
=========================== */
(function () {
    const nav = document.getElementById('mainNav');
    if (!nav) return;
    const applyShadow = () => {
        nav.style.boxShadow = window.scrollY > 10
            ? '0 4px 16px rgba(0,0,0,0.10)'
            : 'none';
    };
    window.addEventListener('scroll', applyShadow, { passive: true });
    applyShadow();
})();

/* ===========================
   장바구니 카운트 로드
=========================== */
(function () {
    document.addEventListener('DOMContentLoaded', function () {
        const badge = document.getElementById('cartBadge');
        if (!badge) return;
        fetch('/api/cart/count')
            .then(r => r.ok ? r.json() : null)
            .then(data => {
                if (data && data.count !== undefined) {
                    badge.textContent = data.count;
                }
            })
            .catch(() => {});
    });
})();

/* ===========================
   Counter animation
=========================== */
(function () {
    const counters = document.querySelectorAll('.counter');
    if (!counters.length) return;

    const animate = (el) => {
        const target = parseInt(el.dataset.target, 10);
        const duration = 1800;
        const step = target / (duration / 16);
        let current = 0;
        const timer = setInterval(() => {
            current += step;
            if (current >= target) {
                el.textContent = target.toLocaleString();
                clearInterval(timer);
            } else {
                el.textContent = Math.floor(current).toLocaleString();
            }
        }, 16);
    };

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(e => {
            if (e.isIntersecting) {
                animate(e.target);
                observer.unobserve(e.target);
            }
        });
    }, { threshold: 0.4 });

    counters.forEach(c => observer.observe(c));
})();

/* ===========================
   Scroll reveal (fade-in)
=========================== */
(function () {
    const els = document.querySelectorAll(
        '.product-card, .service-card, .gallery-card, .gallery-thumb, ' +
        '.value-card, .timeline-item, .support-card, .as-step'
    );
    if (!els.length) return;

    els.forEach(el => el.classList.add('reveal'));

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(e => {
            if (e.isIntersecting) {
                e.target.classList.add('revealed');
                observer.unobserve(e.target);
            }
        });
    }, { threshold: 0.1 });

    els.forEach(el => observer.observe(el));
})();

/* ===========================
   Gallery modal
=========================== */
(function () {
    document.querySelectorAll('.gallery-card[data-src]').forEach(card => {
        card.addEventListener('click', () => {
            const img   = document.getElementById('galleryModalImg');
            const title = document.getElementById('galleryModalTitle');
            const desc  = document.getElementById('galleryModalDesc');
            if (img)   img.src = card.dataset.src;
            if (title) title.textContent = card.dataset.title || '';
            if (desc)  desc.textContent  = card.dataset.desc  || '';
        });
    });
})();
