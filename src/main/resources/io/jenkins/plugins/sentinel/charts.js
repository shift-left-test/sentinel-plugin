/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

/*
 * Client-side behavior for Sentinel report views, loaded as a Stapler
 * adjunct. Jenkins enforces a Content-Security-Policy of
 * "script-src 'self'" on plugin views, which blocks inline <script>
 * blocks and inline event handlers, so all JavaScript lives here.
 * Chart data is passed from Jelly via data-* attributes.
 */
(function () {
  'use strict';

  /* Single source of the colors used across the charts. The mutation
     status colors match the Jelly views; accent is the UI green. */
  var COLOR = {
    killed: '#1ea64b',
    survived: '#e6001f',
    skipped: '#9ba7af',
    accent: '#1ea64b',
    score: '#006fe6',
    muted: '#6b7280',
    axisLine: '#d0d5de',
    splitLine: '#e8ecf1'
  };

  function readJson(el, attr) {
    var raw = el.getAttribute(attr);
    if (!raw) {
      return null;
    }
    return JSON.parse(raw);
  }

  /* Runs onResize whenever the element's size changes. ResizeObserver
     covers both window resizes and CSS resize:both dragging, so it
     supersedes a window listener where available. Its initial delivery
     is skipped: the element was just laid out, so redrawing on page
     load would be pure waste. */
  function observeSize(el, onResize) {
    if (typeof ResizeObserver === 'undefined') {
      window.addEventListener('resize', onResize);
      return;
    }
    var first = true;
    new ResizeObserver(function () {
      if (first) {
        first = false;
        return;
      }
      onResize();
    }).observe(el);
  }

  function initChart(el, option, onResize) {
    var chart = echarts.init(el);
    chart.setOption(option);
    observeSize(el, function () {
      chart.resize();
      if (onResize) {
        onResize();
      }
    });
    return chart;
  }

  function trendSeries(trendData) {
    var s = {builds: [], scores: [], killed: [], survived: [], skipped: []};
    for (var i = 0; i < trendData.length; i++) {
      var d = trendData[i];
      s.builds.push('#' + d.buildNumber);
      s.scores.push(Math.round(d.score * 10) / 10);
      s.killed.push(d.killed);
      s.survived.push(d.survived);
      s.skipped.push(d.skipped);
    }
    return s;
  }

  function donutOption(data, colors) {
    var option = {
      tooltip: {trigger: 'item', formatter: '{b}: {c} ({d}%)'},
      legend: {orient: 'vertical', right: 10, top: 'center'},
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {borderRadius: 6, borderColor: '#fff', borderWidth: 2},
        label: {show: false},
        emphasis: {label: {show: true, fontSize: 16, fontWeight: 'bold'}},
        data: data
      }]
    };
    if (colors) {
      option.color = colors;
    }
    return option;
  }

  /* Donut charts on the build report page (Overview tab). The score
     donut uses the fixed status palette (killed/survived/skipped). */
  function initDonuts() {
    var donuts = [
      {id: 'sentinel-donut-chart', colors: null},
      {id: 'sentinel-score-chart',
        colors: [COLOR.killed, COLOR.survived, COLOR.skipped]}
    ];
    for (var i = 0; i < donuts.length; i++) {
      var chartEl = document.getElementById(donuts[i].id);
      if (!chartEl) {
        continue;
      }
      var distData = readJson(chartEl, 'data-dist');
      if (distData) {
        initChart(chartEl, donutOption(distData, donuts[i].colors));
      }
    }
  }

  function trendSizeKey() {
    return 'sentinel-trend-size:' + window.location.pathname;
  }

  /* Restores the user's dragged size for the floating-box trend
     chart. localStorage access can throw (e.g. private browsing),
     so failures fall back to the default size silently. */
  function restoreTrendSize(el) {
    try {
      var saved = window.localStorage.getItem(trendSizeKey());
      if (!saved) {
        return;
      }
      var size = JSON.parse(saved);
      el.style.width = size.w + 'px';
      el.style.height = size.h + 'px';
    } catch (e) {
      /* keep default size */
    }
  }

  var sizeSaveTimer = null;

  /* Remembers the size the user dragged the chart to, coalescing the
     burst of resize callbacks one drag produces into a single write. */
  function saveTrendSize(el) {
    if (sizeSaveTimer) {
      window.clearTimeout(sizeSaveTimer);
    }
    sizeSaveTimer = window.setTimeout(function () {
      try {
        window.localStorage.setItem(trendSizeKey(), JSON.stringify(
          {w: el.offsetWidth, h: el.offsetHeight}));
      } catch (e) {
        /* size just won't persist */
      }
    }, 200);
  }

  /* Stacked mutation counts with the mutation score overlaid on a
     second axis. Both trend charts share this; compact tunes it for
     the small job page box - no axis names, tighter margins, smaller
     type - so the two charts stay the same chart at two sizes. */
  function trendOption(s, compact) {
    var fontSize = compact ? 10 : 12;
    return {
      tooltip: {
        trigger: 'axis',
        formatter: function (params) {
          var lines = [params[0].axisValue];
          for (var i = 0; i < params.length; i++) {
            lines.push(params[i].marker + ' ' + params[i].seriesName
              + ': <b>' + params[i].value
              + (params[i].seriesName === 'Score' ? '%' : '') + '</b>');
          }
          return lines.join('<br/>');
        }
      },
      legend: {
        bottom: 0,
        itemHeight: fontSize,
        textStyle: {fontSize: fontSize, color: COLOR.muted}
      },
      grid: compact
        ? {left: 40, right: 44, top: 12, bottom: 40}
        : {left: 60, right: 60, top: 20, bottom: 50},
      xAxis: {
        type: 'category',
        data: s.builds,
        axisLabel: {fontSize: fontSize, color: COLOR.muted},
        axisLine: {lineStyle: {color: COLOR.axisLine}}
      },
      yAxis: [
        {
          type: 'value',
          name: compact ? '' : 'Count',
          axisLabel: {fontSize: fontSize, color: COLOR.muted},
          splitLine: {lineStyle: {color: COLOR.splitLine}}
        },
        {
          type: 'value',
          name: compact ? '' : 'Score %',
          min: 0,
          max: 100,
          axisLabel: {
            fontSize: fontSize, color: COLOR.muted, formatter: '{value}%'},
          splitLine: {show: false}
        }
      ],
      series: [
        {name: 'Killed', type: 'bar', stack: 'mutations',
          color: COLOR.killed, data: s.killed},
        {name: 'Survived', type: 'bar', stack: 'mutations',
          color: COLOR.survived, data: s.survived},
        {name: 'Skipped', type: 'bar', stack: 'mutations',
          color: COLOR.skipped, data: s.skipped},
        {name: 'Score', type: 'line', yAxisIndex: 1, color: COLOR.score,
          lineStyle: {width: 2}, symbol: 'circle',
          symbolSize: compact ? 4 : 6, data: s.scores}
      ]
    };
  }

  /* Compact mutation trend in the job page floating box. */
  function initTrendBox() {
    var chartEl = document.getElementById('sentinel-trend-chart');
    if (!chartEl) {
      return;
    }
    var trendData = readJson(chartEl, 'data-trend');
    if (!trendData || trendData.length === 0) {
      return;
    }
    restoreTrendSize(chartEl);
    initChart(chartEl, trendOption(trendSeries(trendData), true),
      function () {
        saveTrendSize(chartEl);
      });
  }

  /* Full trend chart on the Sentinel Trend Report page. */
  function initTrendPage() {
    var chartEl = document.getElementById('sentinel-trend-full');
    if (!chartEl) {
      return;
    }
    var trendData = readJson(chartEl, 'data-trend');
    if (!trendData || trendData.length === 0) {
      var msg = document.createElement('p');
      msg.setAttribute(
        'style',
        'color:' + COLOR.muted + ';padding:40px;text-align:center;');
      msg.textContent = 'No trend data available yet.';
      chartEl.appendChild(msg);
      return;
    }
    initChart(chartEl, trendOption(trendSeries(trendData), false));
  }

  /* Tab switching on the build report page. */
  function showTab(name) {
    var tabs = ['overview', 'files', 'mutations'];
    for (var i = 0; i < tabs.length; i++) {
      var t = tabs[i];
      var panel = document.getElementById('sentinel-panel-' + t);
      var btn = document.getElementById('sentinel-tab-' + t);
      if (t === name) {
        panel.style.display = 'block';
        btn.style.borderBottomColor = COLOR.accent;
        btn.style.color = '#333';
        btn.style.fontWeight = '600';
      } else {
        panel.style.display = 'none';
        btn.style.borderBottomColor = 'transparent';
        btn.style.color = COLOR.muted;
        btn.style.fontWeight = '500';
      }
    }
  }

  function initTabs() {
    var buttons = document.querySelectorAll('[data-sentinel-tab]');
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].addEventListener('click', function () {
        showTab(this.getAttribute('data-sentinel-tab'));
      });
    }
  }

  /* Status/file filters on the Mutations tab. */
  function filterMutations() {
    var statusVal = document.getElementById('sentinel-filter-status').value;
    var fileVal = document.getElementById('sentinel-filter-file').value;
    var rows = document.getElementsByClassName('sentinel-mutation-row');
    for (var i = 0; i < rows.length; i++) {
      var row = rows[i];
      var showStatus = statusVal === 'all'
        || row.getAttribute('data-status') === statusVal;
      var showFile = fileVal === 'all'
        || row.getAttribute('data-file') === fileVal;
      row.style.display = showStatus && showFile ? '' : 'none';
    }
  }

  function initFilters() {
    var statusEl = document.getElementById('sentinel-filter-status');
    var fileEl = document.getElementById('sentinel-filter-file');
    if (!statusEl || !fileEl) {
      return;
    }
    statusEl.addEventListener('change', filterMutations);
    fileEl.addEventListener('change', filterMutations);
  }

  function init() {
    initTabs();
    initFilters();
    if (typeof echarts !== 'undefined') {
      initDonuts();
      initTrendBox();
      initTrendPage();
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
