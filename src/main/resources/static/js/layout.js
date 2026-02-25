/**
 * layout.js — Sidebar toggle + dark mode
 * Place in: src/main/resources/static/js/layout.js
 */

(function () {
    'use strict';

    // --- Elements ---
    const root       = document.documentElement;
    const sidebar    = document.getElementById('sidebar');
    const overlay    = document.getElementById('sidebar-overlay');
    const openBtn    = document.getElementById('sidebar-open');
    const closeBtn   = document.getElementById('sidebar-close');
    const wrapper    = document.getElementById('main-wrapper');

    const toggle     = document.getElementById('theme-toggle');
    const sun        = document.getElementById('icon-sun');
    const moon       = document.getElementById('icon-moon');
    const themeLabel = document.getElementById('theme-label');

    // --- Dark mode ---
    function applyTheme(dark) {
        root.classList.toggle('dark', dark);
        root.classList.toggle('light', !dark);
        if (sun)        sun.classList.toggle('hidden', !dark);
        if (moon)       moon.classList.toggle('hidden', dark);
        if (themeLabel) themeLabel.textContent = dark ? 'Light mode' : 'Dark mode';
        localStorage.setItem('theme', dark ? 'dark' : 'light');
    }

    var stored     = localStorage.getItem('theme');
    var prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    applyTheme(stored ? stored === 'dark' : prefersDark);

    if (toggle) {
        toggle.addEventListener('click', function () {
            applyTheme(!root.classList.contains('dark'));
        });
    }

    // --- Sidebar ---
    var isDesktop = function () { return window.innerWidth >= 1024; };

    function openSidebar() {
        sidebar.classList.remove('-translate-x-full');
        if (!isDesktop() && overlay) overlay.classList.remove('hidden');
    }

    function closeSidebar() {
        sidebar.classList.add('-translate-x-full');
        if (overlay) overlay.classList.add('hidden');
    }

    // Desktop: remember collapsed state
    function applyDesktopPref() {
        var pref = localStorage.getItem('sidebar');
        if (pref === 'closed') {
            sidebar.classList.add('-translate-x-full');
            sidebar.classList.remove('lg:translate-x-0');
            if (wrapper) wrapper.classList.remove('lg:pl-64');
        }
    }

    if (isDesktop()) applyDesktopPref();

    if (openBtn) {
        openBtn.addEventListener('click', function () {
            if (isDesktop()) {
                // Re-open collapsed sidebar on desktop
                sidebar.classList.remove('-translate-x-full');
                sidebar.classList.add('lg:translate-x-0');
                if (wrapper) wrapper.classList.add('lg:pl-64');
                localStorage.setItem('sidebar', 'open');
            } else {
                openSidebar();
            }
        });
    }

    if (closeBtn) {
        closeBtn.addEventListener('click', function () {
            if (isDesktop()) {
                // Collapse sidebar on desktop
                sidebar.classList.add('-translate-x-full');
                sidebar.classList.remove('lg:translate-x-0');
                if (wrapper) wrapper.classList.remove('lg:pl-64');
                localStorage.setItem('sidebar', 'closed');
            } else {
                closeSidebar();
            }
        });
    }

    if (overlay) {
        overlay.addEventListener('click', closeSidebar);
    }

    // Handle resize: if going from mobile→desktop, reapply pref
    window.addEventListener('resize', function () {
        if (isDesktop()) {
            if (overlay) overlay.classList.add('hidden');
            applyDesktopPref();
        }
    });

})();