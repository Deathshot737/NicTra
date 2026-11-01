// Feed Admin · NicTra — mejoras de UI sin dependencias.
(function () {
  'use strict';

  var root = document.documentElement;

  /* ---------- tema claro / oscuro ---------- */
  var THEME_KEY = 'feed-admin-theme';

  function currentTheme() {
    var stored = null;
    try { stored = localStorage.getItem(THEME_KEY); } catch (e) {}
    if (stored === 'light' || stored === 'dark') return stored;
    return root.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
  }

  function applyTheme(theme) {
    root.setAttribute('data-theme', theme);
    try { localStorage.setItem(THEME_KEY, theme); } catch (e) {}
    document.querySelectorAll('[data-theme-toggle]').forEach(function (btn) {
      btn.setAttribute('title', theme === 'dark' ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro');
    });
  }

  applyTheme(currentTheme());
  document.querySelectorAll('[data-theme-toggle]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      applyTheme(currentTheme() === 'dark' ? 'light' : 'dark');
    });
  });

  /* ---------- menú lateral (móvil) ---------- */
  var navToggle = document.getElementById('nav-toggle');
  var scrim = document.getElementById('scrim');
  var sidebar = document.getElementById('sidebar');

  function setNav(open) {
    document.body.classList.toggle('nav-open', open);
    if (navToggle) navToggle.setAttribute('aria-expanded', open ? 'true' : 'false');
  }

  if (navToggle) {
    navToggle.addEventListener('click', function () {
      setNav(!document.body.classList.contains('nav-open'));
    });
  }
  if (scrim) scrim.addEventListener('click', function () { setNav(false); });
  if (sidebar) {
    sidebar.addEventListener('click', function (e) {
      if (e.target.closest('a')) setNav(false);
    });
  }
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') setNav(false);
  });
  window.addEventListener('resize', function () {
    if (window.innerWidth > 980) setNav(false);
  });

  /* ---------- tabla: búsqueda, orden y paginación ---------- */
  var table = document.getElementById('data-table');
  if (table) {
    var tbody = table.tBodies[0];
    var allRows = Array.prototype.slice.call(tbody.rows);
    var ths = Array.prototype.slice.call(table.querySelectorAll('thead th[data-type]'));
    var search = document.getElementById('table-search');
    var emptyMsg = document.getElementById('table-empty');
    var foot = document.getElementById('table-foot');
    var info = document.getElementById('table-info');
    var pager = document.getElementById('pager-pages');
    var sizeSel = document.getElementById('page-size');
    var collator = new Intl.Collator('es', { numeric: true, sensitivity: 'base' });
    var state = { q: '', page: 1, size: 25, col: -1, dir: 1 };

    allRows.forEach(function (row) {
      row._text = row.textContent.toLowerCase();
    });

    function filtered() {
      if (!state.q) return allRows.slice();
      return allRows.filter(function (r) { return r._text.indexOf(state.q) !== -1; });
    }

    function sorted(rows) {
      if (state.col < 0) return rows;
      var type = ths[state.col].getAttribute('data-type');
      var col = state.col;
      return rows.slice().sort(function (a, b) {
        var av = a.cells[col] ? a.cells[col].textContent.trim() : '';
        var bv = b.cells[col] ? b.cells[col].textContent.trim() : '';
        if (type === 'number' || type === 'int') {
          var an = parseFloat(av.replace(',', '.'));
          var bn = parseFloat(bv.replace(',', '.'));
          if (!isNaN(an) && !isNaN(bn)) return (an - bn) * state.dir;
        }
        return collator.compare(av, bv) * state.dir;
      });
    }

    function pageButton(label, page, disabled, aria) {
      var b = document.createElement('button');
      b.type = 'button';
      b.className = 'page-btn';
      b.textContent = label;
      b.disabled = !!disabled;
      if (aria) b.setAttribute('aria-label', aria);
      b.addEventListener('click', function () {
        state.page = page;
        render();
      });
      return b;
    }

    function pageNumbers(current, total) {
      var out = [1];
      if (current > 3) out.push('…');
      for (var p = Math.max(2, current - 1); p <= Math.min(total - 1, current + 1); p++) out.push(p);
      if (current < total - 2) out.push('…');
      if (total > 1) out.push(total);
      return out;
    }

    function render() {
      var rows = sorted(filtered());
      var pages = Math.max(1, Math.ceil(rows.length / state.size));
      if (state.page > pages) state.page = pages;
      var start = (state.page - 1) * state.size;
      var end = Math.min(start + state.size, rows.length);

      allRows.forEach(function (r) { r.hidden = true; });
      rows.slice(start, end).forEach(function (r) { r.hidden = false; });
      // keep DOM order in sync with the active sort
      rows.forEach(function (r) { tbody.appendChild(r); });

      if (emptyMsg) emptyMsg.hidden = rows.length !== 0;
      table.hidden = rows.length === 0;
      if (foot) foot.hidden = rows.length === 0;

      if (info) {
        info.textContent = rows.length
          ? 'Mostrando ' + (start + 1) + '–' + end + ' de ' + rows.length + ' registro' + (rows.length === 1 ? '' : 's')
          : '';
      }

      if (pager) {
        pager.innerHTML = '';
        if (pages > 1) {
          pager.appendChild(pageButton('‹', state.page - 1, state.page === 1, 'Página anterior'));
          pageNumbers(state.page, pages).forEach(function (p) {
            if (p === '…') {
              var gap = document.createElement('span');
              gap.className = 'page-gap';
              gap.textContent = '…';
              pager.appendChild(gap);
              return;
            }
            var b = pageButton(String(p), p, false, 'Página ' + p);
            if (p === state.page) b.setAttribute('aria-current', 'page');
            pager.appendChild(b);
          });
          pager.appendChild(pageButton('›', state.page + 1, state.page === pages, 'Página siguiente'));
        }
      }
    }

    ths.forEach(function (th, idx) {
      var btn = th.querySelector('.th-sort');
      if (!btn) return;
      btn.addEventListener('click', function () {
        if (state.col === idx) {
          state.dir = -state.dir;
        } else {
          state.col = idx;
          state.dir = 1;
        }
        ths.forEach(function (t) { t.removeAttribute('aria-sort'); });
        th.setAttribute('aria-sort', state.dir === 1 ? 'ascending' : 'descending');
        state.page = 1;
        render();
      });
    });

    if (search) {
      search.addEventListener('input', function () {
        state.q = search.value.trim().toLowerCase();
        state.page = 1;
        render();
      });

      // "/" enfoca la búsqueda, como en los paneles de código.
      document.addEventListener('keydown', function (e) {
        if (e.key !== '/' || e.target.matches('input, textarea, select')) return;
        e.preventDefault();
        search.focus();
      });
    }

    if (sizeSel) {
      sizeSel.addEventListener('change', function () {
        state.size = parseInt(sizeSel.value, 10) || 25;
        state.page = 1;
        render();
      });
    }

    render();
  }

  /* ---------- asignaciones (delegaciones ↔ trámites) ---------- */
  document.querySelectorAll('[data-assign]').forEach(function (section) {
    var items = Array.prototype.slice.call(section.querySelectorAll('.assign-item'));
    var boxes = items.map(function (it) { return it.querySelector('input[type="checkbox"]'); });
    var search = section.querySelector('.assign-search');
    var count = section.querySelector('[data-assign-count]');
    var total = items.length;

    function sync(item, box) {
      if (item) item.classList.toggle('is-checked', box.checked);
    }

    function updateCount() {
      if (!count) return;
      var n = boxes.filter(function (b) { return b.checked; }).length;
      count.textContent = n
        ? n + ' de ' + total + ' seleccionadas'
        : total + ' disponibles';
    }

    boxes.forEach(function (box) {
      sync(box.closest('.assign-item'), box);
      box.addEventListener('change', function () {
        sync(box.closest('.assign-item'), box);
        updateCount();
      });
    });

    if (search) {
      search.addEventListener('input', function () {
        var q = search.value.trim().toLowerCase();
        items.forEach(function (it) {
          it.classList.toggle('is-hidden', q !== '' && it.textContent.toLowerCase().indexOf(q) === -1);
        });
      });
    }

    section.querySelectorAll('[data-assign-all]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        items.forEach(function (it, i) {
          if (it.classList.contains('is-hidden')) return;
          boxes[i].checked = true;
          sync(it, boxes[i]);
        });
        updateCount();
      });
    });

    section.querySelectorAll('[data-assign-none]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        boxes.forEach(function (box, i) {
          box.checked = false;
          sync(items[i], box);
        });
        updateCount();
      });
    });

    updateCount();
  });

  /* ---------- avisos ---------- */
  document.querySelectorAll('[data-flash]').forEach(function (el) {
    setTimeout(function () {
      el.classList.add('is-dismissed');
      setTimeout(function () { el.remove(); }, 400);
    }, 5000);
  });
})();
