// Minimal browser polyfills for Aliyun captcha SDK under GraalJS.
// Host injects: globalThis.__javaHttp (JsHttpBridge), globalThis.__cryptoJsSource (optional string)
(function (g) {
  "use strict";

  if (typeof g.console === "undefined") {
    g.console = { log: function () {}, error: function () {}, warn: function () {}, info: function () {}, debug: function () {} };
  }

  function b64encode(binary) {
    var chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    var str = String(binary);
    var out = "";
    var i = 0;
    while (i < str.length) {
      var c1 = str.charCodeAt(i++) & 255;
      var c2 = i < str.length ? str.charCodeAt(i++) & 255 : NaN;
      var c3 = i < str.length ? str.charCodeAt(i++) & 255 : NaN;
      var e1 = c1 >> 2;
      var e2 = ((c1 & 3) << 4) | (isNaN(c2) ? 0 : c2 >> 4);
      var e3 = isNaN(c2) ? 64 : (((c2 & 15) << 2) | (isNaN(c3) ? 0 : c3 >> 6));
      var e4 = isNaN(c3) ? 64 : (c3 & 63);
      out += chars.charAt(e1) + chars.charAt(e2) + (e3 === 64 ? "=" : chars.charAt(e3)) + (e4 === 64 ? "=" : chars.charAt(e4));
    }
    return out;
  }

  function b64decode(data) {
    var chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    var str = String(data).replace(/=+$/, "");
    var out = "";
    var i = 0;
    while (i < str.length) {
      var e1 = chars.indexOf(str.charAt(i++));
      var e2 = chars.indexOf(str.charAt(i++));
      var e3 = chars.indexOf(str.charAt(i++));
      var e4 = chars.indexOf(str.charAt(i++));
      var c1 = (e1 << 2) | (e2 >> 4);
      var c2 = ((e2 & 15) << 4) | (e3 >> 2);
      var c3 = ((e3 & 3) << 6) | e4;
      out += String.fromCharCode(c1);
      if (e3 !== -1 && str.charAt(i - 2) !== undefined && e3 < 64) out += String.fromCharCode(c2);
      if (e4 !== -1 && e4 < 64) out += String.fromCharCode(c3);
    }
    return out;
  }

  g.btoa = g.btoa || function (s) { return b64encode(s); };
  g.atob = g.atob || function (s) { return b64decode(s); };
  // Remote script tags call window.eval(code); ensure it exists on globalThis.
  g.eval = g.eval || function (code) { return (0, eval)(code); };

  // Early progress log (used by XHR/script before solve helpers are defined).
  g.__progress = [];
  g.__logProgress = function (msg) {
    try {
      g.__progress.push(Date.now() + " " + msg);
      if (g.__progress.length > 100) g.__progress.shift();
    } catch (e) {}
  };

  if (typeof g.navigator === "undefined") g.navigator = {};
  var nav = {
    webdriver: false,
    hardwareConcurrency: 10,
    deviceMemory: 32,
    platform: "MacIntel",
    vendor: "Google Inc.",
    maxTouchPoints: 0,
    plugins: [],
    mimeTypes: [],
    language: "zh-CN",
    languages: ["zh-CN", "zh", "en"],
    userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36",
    appVersion: "5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36",
    cookieEnabled: true,
    onLine: true
  };
  for (var nk in nav) {
    if (Object.prototype.hasOwnProperty.call(nav, nk)) {
      try {
        Object.defineProperty(g.navigator, nk, { get: (function (v) { return function () { return v; }; })(nav[nk]), configurable: true });
      } catch (e) {
        g.navigator[nk] = nav[nk];
      }
    }
  }

  if (typeof g.screen === "undefined") g.screen = {};
  var screenProps = { width: 1728, height: 1117, availWidth: 1728, availHeight: 1084, colorDepth: 30, pixelDepth: 30 };
  for (var sk in screenProps) {
    if (Object.prototype.hasOwnProperty.call(screenProps, sk)) {
      try {
        Object.defineProperty(g.screen, sk, { get: (function (v) { return function () { return v; }; })(screenProps[sk]), configurable: true });
      } catch (e) {
        g.screen[sk] = screenProps[sk];
      }
    }
  }

  try { Object.defineProperty(g, "innerWidth", { get: function () { return 1728; }, configurable: true }); } catch (e) { g.innerWidth = 1728; }
  try { Object.defineProperty(g, "innerHeight", { get: function () { return 997; }, configurable: true }); } catch (e) { g.innerHeight = 997; }
  try { Object.defineProperty(g, "outerWidth", { get: function () { return 1728; }, configurable: true }); } catch (e) { g.outerWidth = 1728; }
  try { Object.defineProperty(g, "outerHeight", { get: function () { return 1084; }, configurable: true }); } catch (e) { g.outerHeight = 1084; }
  try { Object.defineProperty(g, "devicePixelRatio", { get: function () { return 2; }, configurable: true }); } catch (e) { g.devicePixelRatio = 2; }
  g.self = g;
  g.window = g;
  g.top = g;
  g.parent = g;
  g.frames = g;
  g.name = "";
  g.location = g.location || {
    href: "https://uc.perfect99.com/loginAndRegistration",
    protocol: "https:",
    host: "uc.perfect99.com",
    hostname: "uc.perfect99.com",
    port: "",
    pathname: "/loginAndRegistration",
    search: "",
    hash: "",
    origin: "https://uc.perfect99.com",
    toString: function () { return this.href; }
  };
  g.document = g.document || {};
  g.document.URL = g.location.href;
  g.document.documentURI = g.location.href;
  g.document.location = g.location;
  g.document.domain = "uc.perfect99.com";
  g.document.referrer = "https://uc.perfect99.com/";
  g.document.cookie = "";
  g.document.title = "login";
  g.document.hidden = false;
  g.document.visibilityState = "visible";
  g.document.readyState = "complete";
  g.document.characterSet = "UTF-8";
  g.document.compatMode = "CSS1Compat";

  if (!g.performance) g.performance = {};
  g.performance.now = function () { return Date.now(); };
  g.performance.memory = { jsHeapSizeLimit: 4294705152, totalJSHeapSize: 35000000, usedJSHeapSize: 25000000 };
  g.performance.timing = { navigationStart: Date.now() - 1000 };

  if (!g.crypto) g.crypto = {};
  g.crypto.getRandomValues = function (arr) {
    for (var i = 0; i < arr.length; i++) arr[i] = Math.floor(Math.random() * 256);
    return arr;
  };

  // GraalJS has no browser event loop; host drains these from CaptchaJsRuntime.awaitValue.
  g.__timers = [];
  g.__timerSeq = 1;
  g.setTimeout = function (fn, ms) {
    var id = g.__timerSeq++;
    var delay = typeof ms === "number" && ms > 0 ? ms : 0;
    g.__timers.push({ id: id, due: Date.now() + delay, fn: fn, interval: 0 });
    return id;
  };
  g.clearTimeout = function (id) {
    g.__timers = g.__timers.filter(function (t) { return t.id !== id; });
  };
  g.setInterval = function (fn, ms) {
    var id = g.__timerSeq++;
    var delay = typeof ms === "number" && ms > 0 ? ms : 0;
    g.__timers.push({ id: id, due: Date.now() + delay, fn: fn, interval: delay || 1 });
    return id;
  };
  g.clearInterval = g.clearTimeout;
  g.__drainTimers = function () {
    var now = Date.now();
    var fired = 0;
    var keep = [];
    var due = [];
    for (var i = 0; i < g.__timers.length; i++) {
      var t = g.__timers[i];
      if (t.due <= now) due.push(t);
      else keep.push(t);
    }
    g.__timers = keep;
    for (var j = 0; j < due.length; j++) {
      var task = due[j];
      try { if (typeof task.fn === "function") task.fn(); } catch (e) {}
      fired++;
      if (task.interval > 0) {
        task.due = Date.now() + task.interval;
        g.__timers.push(task);
      }
    }
    return fired;
  };

  g.requestAnimationFrame = function (cb) { return g.setTimeout(function () { cb(Date.now()); }, 16); };
  g.cancelAnimationFrame = function (id) { g.clearTimeout(id); };

  g.MutationObserver = g.MutationObserver || function () { this.observe = function () {}; this.disconnect = function () {}; this.takeRecords = function () { return []; }; };
  g.ResizeObserver = g.ResizeObserver || function () { this.observe = function () {}; this.disconnect = function () {}; this.unobserve = function () {}; };
  g.IntersectionObserver = g.IntersectionObserver || function () { this.observe = function () {}; this.disconnect = function () {}; this.unobserve = function () {}; };
  g.OfflineAudioContext = g.OfflineAudioContext || function (ch, len, sr) {
    this.sampleRate = sr || 44100;
    this.length = len || 44100;
    this.numberOfChannels = ch || 1;
    this.destination = { channelCount: 1 };
    this.createOscillator = function () { return { type: "sine", frequency: { value: 440 }, connect: function () {}, start: function () {}, stop: function () {} }; };
    this.createDynamicsCompressor = function () { return { threshold: { value: -24 }, knee: { value: 30 }, ratio: { value: 12 }, connect: function () {} }; };
    this.createGain = function () { return { gain: { value: 1 }, connect: function () {} }; };
    this.startRendering = function () {
      var self = this;
      return Promise.resolve({ getChannelData: function () { return new Float32Array(self.length); } });
    };
  };
  g.AudioContext = g.AudioContext || g.OfflineAudioContext;
  g.WebSocket = g.WebSocket || function (url) { this.url = url; this.readyState = 0; this.send = function () {}; this.close = function () {}; this.addEventListener = function () {}; };
  g.Worker = g.Worker || function () { this.postMessage = function () {}; this.terminate = function () {}; this.addEventListener = function () {}; };
  g.matchMedia = g.matchMedia || function (q) {
    return { matches: false, media: q, onchange: null, addListener: function () {}, removeListener: function () {}, addEventListener: function () {}, removeEventListener: function () {}, dispatchEvent: function () { return false; } };
  };
  g.getComputedStyle = g.getComputedStyle || function () {
    return { getPropertyValue: function () { return ""; }, setProperty: function () {}, getPropertyPriority: function () { return ""; } };
  };
  // dynamicJS (sg.*) references these browser window APIs at top-level.
  ["moveTo", "moveBy", "resizeTo", "resizeBy", "scrollTo", "scrollBy", "scroll", "focus", "blur", "open", "close", "print", "stop", "alert", "confirm", "prompt"].forEach(function (n) {
    if (typeof g[n] !== "function") g[n] = function () {};
  });
  ["CSSRule", "CSSStyleRule", "CSSStyleSheet", "CSSRuleList", "CSSStyleDeclaration", "HTMLMediaElement", "MediaSource", "SourceBuffer"].forEach(function (n) {
    if (typeof g[n] === "undefined") g[n] = function () {};
  });
  if (typeof g.DOMParser === "undefined") {
    g.DOMParser = function () {
      this.parseFromString = function () {
        return g.document;
      };
    };
  }
  // HTMLMediaElement / Audio stubs required by dynamicJS top-level.
  if (typeof g.Audio === "undefined") {
    g.Audio = function () {
      this.src = "";
      this.volume = 1;
      this.muted = false;
      this.paused = true;
      this.currentTime = 0;
      this.duration = 0;
      this.readyState = 0;
      this.networkState = 0;
      this.play = function () { this.paused = false; return Promise.resolve(); };
      this.pause = function () { this.paused = true; };
      this.load = function () {};
      this.canPlayType = function () { return ""; };
      this.addEventListener = function () {};
      this.removeEventListener = function () {};
      this.dispatchEvent = function () { return true; };
      this.cloneNode = function () { return new g.Audio(); };
    };
  }
  if (typeof g.HTMLAudioElement === "undefined") g.HTMLAudioElement = g.Audio;
  if (typeof g.speechSynthesis === "undefined") {
    g.speechSynthesis = { speak: function () {}, cancel: function () {}, getVoices: function () { return []; }, paused: false, pending: false, speaking: false };
  }
  if (typeof g.Notification === "undefined") {
    g.Notification = function () {};
    g.Notification.permission = "default";
    g.Notification.requestPermission = function () { return Promise.resolve("default"); };
  }
  if (typeof g.Blob === "undefined") {
    g.Blob = function (parts, opts) {
      this.size = 0;
      this.type = (opts && opts.type) || "";
      this.parts = parts || [];
    };
  }
  if (typeof g.URL === "undefined") {
    g.URL = function (url, base) {
      var s = String(url || "");
      if (base && s.indexOf("http") !== 0) {
        try {
          var b = String(base);
          if (s.charAt(0) === "/") {
            var m = b.match(/^(https?:\/\/[^/]+)/);
            s = (m ? m[1] : b) + s;
          } else {
            s = b.replace(/\/?$/, "/") + s.replace(/^\.\//, "");
          }
        } catch (e) {}
      }
      this.href = s;
      var mm = s.match(/^(https?:)\/\/([^/?#]+)([^?#]*)(\?[^#]*)?(#.*)?$/);
      this.protocol = mm ? mm[1] : "https:";
      this.host = mm ? mm[2] : "";
      this.hostname = this.host.split(":")[0];
      this.port = (this.host.indexOf(":") >= 0) ? this.host.split(":")[1] : "";
      this.pathname = mm ? (mm[3] || "/") : "/";
      this.search = mm && mm[4] ? mm[4] : "";
      this.hash = mm && mm[5] ? mm[5] : "";
      this.origin = this.protocol + "//" + this.host;
      this.toString = function () { return this.href; };
    };
    g.URL.createObjectURL = function () { return "blob:yc-stub"; };
    g.URL.revokeObjectURL = function () {};
  }
  if (typeof g.FileReader === "undefined") {
    g.FileReader = function () {
      this.result = null;
      this.onload = null;
      this.readAsDataURL = function () { this.result = "data:,"; if (this.onload) this.onload(); };
      this.readAsText = function (b) { this.result = ""; if (this.onload) this.onload(); };
    };
  }
  if (typeof g.TextEncoder === "undefined") {
    g.TextEncoder = function () {
      this.encode = function (s) {
        s = String(s || "");
        var arr = [];
        for (var i = 0; i < s.length; i++) {
          var c = s.charCodeAt(i);
          if (c < 128) arr.push(c);
          else if (c < 2048) { arr.push(192 | (c >> 6), 128 | (c & 63)); }
          else { arr.push(224 | (c >> 12), 128 | ((c >> 6) & 63), 128 | (c & 63)); }
        }
        return new Uint8Array(arr);
      };
    };
  }
  if (typeof g.TextDecoder === "undefined") {
    g.TextDecoder = function () {
      this.decode = function (buf) {
        var a = buf instanceof Uint8Array ? buf : new Uint8Array(buf || []);
        var out = "";
        for (var i = 0; i < a.length; i++) out += String.fromCharCode(a[i]);
        try { return decodeURIComponent(escape(out)); } catch (e) { return out; }
      };
    };
  }
  if (typeof g.History === "undefined") g.History = function () {};
  if (typeof g.Option === "undefined") g.Option = function (text, value) { this.text = text; this.value = value != null ? value : text; this.selected = false; this.disabled = false; };
  if (typeof g.HTMLOptionElement === "undefined") g.HTMLOptionElement = g.Option;
  if (typeof g.HTMLSelectElement === "undefined") g.HTMLSelectElement = function () {};
  if (typeof g.HTMLInputElement === "undefined") g.HTMLInputElement = function () {};
  if (typeof g.HTMLFormElement === "undefined") g.HTMLFormElement = function () {};
  if (typeof g.HTMLAnchorElement === "undefined") g.HTMLAnchorElement = function () {};
  if (typeof g.HTMLButtonElement === "undefined") g.HTMLButtonElement = function () {};
  if (typeof g.NodeList === "undefined") g.NodeList = function () {};
  if (typeof g.HTMLCollection === "undefined") g.HTMLCollection = function () {};
  if (typeof g.NamedNodeMap === "undefined") g.NamedNodeMap = function () {};

  if (typeof g.history === "undefined") {
    g.history = {
      length: 1,
      state: null,
      scrollRestoration: "auto",
      pushState: function () {},
      replaceState: function () {},
      go: function () {},
      back: function () {},
      forward: function () {}
    };
  }
  function makeStorage() {
    var store = {};
    return {
      getItem: function (k) { return Object.prototype.hasOwnProperty.call(store, k) ? store[k] : null; },
      setItem: function (k, v) { store[k] = String(v); },
      removeItem: function (k) { delete store[k]; },
      clear: function () { for (var k in store) if (Object.prototype.hasOwnProperty.call(store, k)) delete store[k]; },
      key: function () { return null; },
      get length() { return Object.keys(store).length; }
    };
  }
  if (typeof g.localStorage === "undefined") g.localStorage = makeStorage();
  if (typeof g.sessionStorage === "undefined") g.sessionStorage = makeStorage();
  if (typeof g.queueMicrotask !== "function") {
    g.queueMicrotask = function (fn) { g.setTimeout(fn, 0); };
  }
  // Auto-define missing browser globals when dynamicJS top-level eval fails with ReferenceError.
  g.__ensureGlobal = function (name) {
    if (!name || typeof name !== "string") return;
    if (name in g) return;
    try {
      // Constructors often capitalized; methods/objects lower/mixed.
      if (/^[A-Z]/.test(name)) g[name] = function () {};
      else g[name] = function () {};
      g.__logProgress("auto-global " + name);
    } catch (e) {}
  };
  g.__evalWithAutoGlobals = function (code, label) {
    var tries = 0;
    while (tries < 80) {
      try {
        g.eval(code);
        return true;
      } catch (e) {
        var msg = String((e && (e.message || e)) || "");
        var m = msg.match(/([A-Za-z_$][\w$]*) is not defined/);
        if (!m) {
          g.__logProgress((label || "eval") + " err " + msg.slice(0, 120));
          throw e;
        }
        g.__ensureGlobal(m[1]);
        tries++;
      }
    }
    g.__logProgress((label || "eval") + " too many missing globals");
    return false;
  };

  // Tiny DOM tree for captcha mount point
  function El(tag) {
    this.tagName = String(tag || "div").toUpperCase();
    this._id = "";
    this.className = "";
    this.style = {};
    this.children = [];
    this.parentNode = null;
    this.ownerDocument = g.document;
    this.attributes = {};
    this._listeners = {};
    this._innerHTML = "";
    this._textContent = "";
    this.value = "";
    this.src = "";
    this.href = "";
    this.type = "";
    this.width = 0;
    this.height = 0;
    this.disabled = false;
    this.checked = false;
    this.nodeType = 1;
  }
  El.prototype.setAttribute = function (k, v) {
    this.attributes[k] = String(v);
    if (k === "id") {
      var old = this.id;
      var nv = String(v);
      if (old && g.document.__byId[old] === this) delete g.document.__byId[old];
      this.id = nv;
      if (nv) g.document.__byId[nv] = this;
    }
    if (k === "class") this.className = String(v);
    if (k === "src") this.src = String(v);
    if (k === "href") this.href = String(v);
    if (k === "type") this.type = String(v);
  };

  // Minimal HTML fragment parser so captcha SDK mount via innerHTML works.
  function parseHtmlFragment(html, owner) {
    html = String(html || "");
    var rootKids = [];
    var stack = [];
    var re = /<!--[\s\S]*?-->|<([a-zA-Z0-9]+)([^>]*)\/>|<([a-zA-Z0-9]+)([^>]*)>|<\/([a-zA-Z0-9]+)>|([^<]+)/g;
    var m;
    function parseAttrs(el, raw) {
      if (!raw) return;
      var ar = /([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))/g;
      var am;
      while ((am = ar.exec(raw))) {
        var name = am[1];
        var val = am[3] != null ? am[3] : (am[4] != null ? am[4] : am[5]);
        el.setAttribute(name, val);
        if (name === "class") el.className = val;
        if (name === "style" && val) {
          val.split(";").forEach(function (pair) {
            var kv = pair.split(":");
            if (kv.length >= 2) el.style[String(kv[0]).trim()] = String(kv.slice(1).join(":")).trim();
          });
        }
      }
    }
    function attach(node) {
      if (stack.length) stack[stack.length - 1].appendChild(node);
      else rootKids.push(node);
    }
    while ((m = re.exec(html))) {
      if (m[1]) { // self-closing
        var el1 = new El(m[1]);
        parseAttrs(el1, m[2]);
        attach(el1);
      } else if (m[3]) { // open
        var el2 = new El(m[3]);
        parseAttrs(el2, m[4]);
        attach(el2);
        var voidTags = { img: 1, br: 1, hr: 1, input: 1, meta: 1, link: 1, col: 1, area: 1, base: 1, embed: 1, source: 1, track: 1, wbr: 1 };
        if (!voidTags[String(m[3]).toLowerCase()]) stack.push(el2);
      } else if (m[5]) { // close
        while (stack.length) {
          var top = stack.pop();
          if (String(top.tagName).toLowerCase() === String(m[5]).toLowerCase()) break;
        }
      } else if (m[6]) {
        var text = m[6];
        if (text && text.replace(/\s+/g, "").length) {
          var tn = new El("#text");
          tn.nodeType = 3;
          tn.textContent = text;
          tn._textContent = text;
          attach(tn);
        }
      }
    }
    return rootKids;
  }

  Object.defineProperty(El.prototype, "innerHTML", {
    get: function () { return this._innerHTML || ""; },
    set: function (html) {
      // clear children registry
      var old = this.children ? this.children.slice() : [];
      for (var i = 0; i < old.length; i++) {
        var c = old[i];
        if (c && c.id && g.document.__byId[c.id] === c) delete g.document.__byId[c.id];
        c.parentNode = null;
      }
      this.children = [];
      this._innerHTML = String(html == null ? "" : html);
      if (!this._innerHTML) return;
      var kids = parseHtmlFragment(this._innerHTML, this);
      for (var j = 0; j < kids.length; j++) this.appendChild(kids[j]);
      // Ensure captcha slider anchors exist if SDK HTML uses expected ids/classes.
      if (String(this.id) === "nc" || String(this.className).indexOf("aliyun") >= 0) {
        g.__ensureCaptchaDom(this);
      }
    },
    configurable: true
  });
  Object.defineProperty(El.prototype, "textContent", {
    get: function () { return this._textContent || ""; },
    set: function (v) {
      this._textContent = String(v == null ? "" : v);
      this.children = [];
      this._innerHTML = "";
    },
    configurable: true
  });

  g.__ensureCaptchaDom = function (root) {
    root = root || g.document.getElementById("nc") || g.document.body;
    if (!root) return;
    function need(id, tag, parent) {
      var el = g.document.getElementById(id);
      if (el) return el;
      el = new El(tag || "div");
      el.id = id;
      (parent || root).appendChild(el);
      return el;
    }
    // Common Aliyun sliding captcha structure used by helpers/tests.
    var body = need("aliyunCaptcha-sliding-body", "div", root);
    need("aliyunCaptcha-sliding-left", "div", body);
    need("aliyunCaptcha-sliding-slider", "div", body);
    need("aliyunCaptcha-sliding-text", "div", body);
  };
  El.prototype.getAttribute = function (k) {
    if (k === "id") return this.id || null;
    if (k === "class") return this.className || null;
    if (k === "src") return this.src || null;
    return this.attributes[k] != null ? this.attributes[k] : null;
  };
  El.prototype.removeAttribute = function (k) {
    delete this.attributes[k];
    if (k === "id") {
      if (this.id && g.document.__byId[this.id] === this) delete g.document.__byId[this.id];
      this.id = "";
    }
  };
  // Property assignment el.id = "x" must also register for getElementById (SDK does this).
  Object.defineProperty(El.prototype, "id", {
    get: function () { return this._id || ""; },
    set: function (v) {
      var old = this._id || "";
      var nv = v == null ? "" : String(v);
      if (old && g.document && g.document.__byId && g.document.__byId[old] === this) {
        delete g.document.__byId[old];
      }
      this._id = nv;
      this.attributes.id = nv;
      if (nv && g.document && g.document.__byId) g.document.__byId[nv] = this;
    },
    configurable: true
  });
  El.prototype.appendChild = function (c) {
    c.parentNode = this;
    this.children.push(c);
    if (c.id) g.document.__byId[c.id] = c;
    // If a script was given src before append, load it now (browser behavior).
    try {
      if (c && c.tagName === "SCRIPT" && c._src && !c.__loaded) {
        c.__loaded = true;
        var src = c._src;
        g.__loadScript(String(src), function (code) {
          try {
            var ok = g.__evalWithAutoGlobals(code, "script");
            if (!ok) throw new Error("script eval failed");
            if (String(src).indexOf("FeiLin") >= 0 || String(src).indexOf("feilin") >= 0) g.__feilinReady = true;
            if (String(src).indexOf("dynamicJS") >= 0) g.__dynamicJsReady = true;
            g.__logProgress("script-eval ok " + String(src).slice(0, 80));
            if (typeof c.onload === "function") c.onload();
            if (c.dispatchEvent) c.dispatchEvent(new g.Event("load"));
          } catch (e) {
            g.__logProgress("script-eval err " + (e && (e.message || e)));
            if (typeof c.onerror === "function") c.onerror(e);
            if (c.dispatchEvent) c.dispatchEvent(new g.Event("error"));
          }
        }, function (err) {
          g.__logProgress("script-load err " + (err && (err.message || err)));
          if (typeof c.onerror === "function") c.onerror(err);
          if (c.dispatchEvent) c.dispatchEvent(new g.Event("error"));
        });
      }
    } catch (e) {}
    return c;
  };
  El.prototype.removeChild = function (c) {
    var i = this.children.indexOf(c);
    if (i >= 0) this.children.splice(i, 1);
    c.parentNode = null;
    return c;
  };
  El.prototype.insertBefore = function (c, ref) {
    c.parentNode = this;
    if (!ref) return this.appendChild(c);
    var i = this.children.indexOf(ref);
    if (i < 0) return this.appendChild(c);
    this.children.splice(i, 0, c);
    if (c.id) g.document.__byId[c.id] = c;
    return c;
  };
  El.prototype.addEventListener = function (type, fn) {
    if (!this._listeners[type]) this._listeners[type] = [];
    this._listeners[type].push(fn);
  };
  El.prototype.removeEventListener = function (type, fn) {
    var arr = this._listeners[type];
    if (!arr) return;
    var i = arr.indexOf(fn);
    if (i >= 0) arr.splice(i, 1);
  };
  El.prototype.dispatchEvent = function (ev) {
    if (!ev) return false;
    if (!ev.target) ev.target = this;
    if (!ev.currentTarget) ev.currentTarget = this;
    var arr = (this._listeners[ev.type] || []).slice();
    for (var i = 0; i < arr.length; i++) {
      try { arr[i].call(this, ev); } catch (e) {}
    }
    var p = "on" + ev.type;
    if (typeof this[p] === "function") {
      try { this[p](ev); } catch (e) {}
    }
    return true;
  };
  El.prototype.getBoundingClientRect = function () {
    if (this.id === "aliyunCaptcha-sliding-slider") return { x: 100, y: 100, width: 40, height: 40, top: 100, left: 100, right: 140, bottom: 140 };
    if (this.id === "aliyunCaptcha-sliding-body") return { x: 100, y: 100, width: 360, height: 40, top: 100, left: 100, right: 460, bottom: 140 };
    if (this.id === "aliyunCaptcha-sliding-left") return { x: 100, y: 100, width: 0, height: 40, top: 100, left: 100, right: 100, bottom: 140 };
    return { x: 0, y: 0, width: 0, height: 0, top: 0, left: 0, right: 0, bottom: 0 };
  };
  El.prototype.querySelector = function (sel) { return g.document.querySelector(sel); };
  El.prototype.querySelectorAll = function (sel) { return g.document.querySelectorAll(sel); };
  El.prototype.contains = function (n) {
    if (n === this) return true;
    for (var i = 0; i < this.children.length; i++) {
      if (this.children[i] === n || (this.children[i].contains && this.children[i].contains(n))) return true;
    }
    return false;
  };
  El.prototype.cloneNode = function () { return new El(this.tagName); };
  El.prototype.focus = function () {};
  El.prototype.blur = function () {};
  El.prototype.click = function () { this.dispatchEvent(new g.MouseEvent("click", { bubbles: true })); };
  El.prototype.getContext = function (type) {
    if (type === "2d") {
      return {
        fillText: function () {}, strokeText: function () {}, measureText: function (s) { return { width: s ? s.length * 8 : 0 }; },
        fillRect: function () {}, clearRect: function () {}, drawImage: function () {}, arc: function () {},
        beginPath: function () {}, closePath: function () {}, fill: function () {}, stroke: function () {},
        save: function () {}, restore: function () {}, translate: function () {}, rotate: function () {}, scale: function () {},
        setTransform: function () {}, createLinearGradient: function () { return { addColorStop: function () {} }; },
        createRadialGradient: function () { return { addColorStop: function () {} }; },
        getImageData: function (x, y, w, h) { return { data: new Uint8Array(w * h * 4) }; },
        putImageData: function () {}, font: "", textBaseline: "alphabetic", textAlign: "start",
        fillStyle: "", strokeStyle: "", lineWidth: 1, globalAlpha: 1, globalCompositeOperation: "source-over"
      };
    }
    if (type === "webgl" || type === "webgl2" || type === "experimental-webgl") {
      return {
        getParameter: function () { return "WebKit WebGL"; },
        getExtension: function () { return null; },
        getSupportedExtensions: function () { return []; },
        createShader: function () { return {}; }, shaderSource: function () {}, compileShader: function () {},
        createProgram: function () { return {}; }, attachShader: function () {}, linkProgram: function () {},
        useProgram: function () {}, getShaderParameter: function () { return true; }, getProgramParameter: function () { return true; },
        createBuffer: function () { return {}; }, bindBuffer: function () {}, bufferData: function () {},
        enableVertexAttribArray: function () {}, vertexAttribPointer: function () {},
        getAttribLocation: function () { return 0; }, getUniformLocation: function () { return {}; },
        uniform1f: function () {}, uniform2f: function () {}, uniform3f: function () {}, uniform4f: function () {},
        uniformMatrix4fv: function () {}, drawArrays: function () {}, viewport: function () {},
        clearColor: function () {}, clear: function () {}, enable: function () {}, disable: function () {}
      };
    }
    return null;
  };
  El.prototype.toDataURL = function () {
    return "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVQI12NgAAIABQABNjN9GQAAAABJRf5ErkJggg==";
  };

  Object.defineProperty(El.prototype, "offsetWidth", {
    get: function () {
      if (this.id === "aliyunCaptcha-sliding-body") return 360;
      if (this.id === "aliyunCaptcha-sliding-slider") return 40;
      return this.width || 0;
    },
    configurable: true
  });
  Object.defineProperty(El.prototype, "offsetHeight", {
    get: function () {
      if (this.id === "aliyunCaptcha-sliding-body") return 40;
      if (this.id === "aliyunCaptcha-sliding-slider") return 40;
      return this.height || 0;
    },
    configurable: true
  });
  Object.defineProperty(El.prototype, "clientWidth", { get: function () { return this.offsetWidth; }, configurable: true });
  Object.defineProperty(El.prototype, "clientHeight", { get: function () { return this.offsetHeight; }, configurable: true });

  g.HTMLElement = El;
  g.Element = El;
  g.Node = El;
  g.HTMLCanvasElement = El;
  g.HTMLDivElement = El;
  g.HTMLScriptElement = El;
  g.HTMLImageElement = El;
  g.EventTarget = El;
  g.Image = function () {
    return g.document.createElement("img");
  };

  g.Event = function (type, init) {
    this.type = type;
    this.bubbles = !!(init && init.bubbles);
    this.cancelable = !!(init && init.cancelable);
    this.defaultPrevented = false;
    this.target = null;
    this.currentTarget = null;
    this.preventDefault = function () { this.defaultPrevented = true; };
    this.stopPropagation = function () {};
    this.stopImmediatePropagation = function () {};
  };
  g.MouseEvent = function (type, init) {
    g.Event.call(this, type, init || {});
    init = init || {};
    this.clientX = init.clientX || 0;
    this.clientY = init.clientY || 0;
    this.screenX = init.screenX || this.clientX;
    this.screenY = init.screenY || this.clientY;
    this.button = init.button || 0;
    this.buttons = init.buttons != null ? init.buttons : 0;
    this.which = init.which || 0;
    this.ctrlKey = !!init.ctrlKey;
    this.shiftKey = !!init.shiftKey;
    this.altKey = !!init.altKey;
    this.metaKey = !!init.metaKey;
    this.view = g;
  };
  g.MouseEvent.prototype = Object.create(g.Event.prototype);
  g.MouseEvent.prototype.constructor = g.MouseEvent;

  g.document.__byId = {};
  g.document.documentElement = new El("html");
  g.document.head = new El("head");
  g.document.body = new El("body");
  g.document.documentElement.appendChild(g.document.head);
  g.document.documentElement.appendChild(g.document.body);
  var nc = new El("div");
  nc.id = "nc";
  g.document.body.appendChild(nc);
  g.document.__byId.nc = nc;

  g.document.createElement = function (tag) {
    var el = new El(tag);
    var lower = String(tag).toLowerCase();
    if (lower === "script") {
      var self = el;
      self.addEventListener = El.prototype.addEventListener;
      self.removeEventListener = El.prototype.removeEventListener;
      self.dispatchEvent = El.prototype.dispatchEvent;
      Object.defineProperty(el, "src", {
        get: function () { return self._src || ""; },
        set: function (v) {
          self._src = v;
          self.attributes.src = v;
          var url = String(v || "");
          if (url.indexOf("//") === 0) url = "https:" + url;
          // Load immediately if already in document, else appendChild will load.
          if (url && (url.indexOf("http") === 0) && self.parentNode) {
            self.__loaded = true;
            g.__loadScript(url, function (code) {
              try {
                var ok = g.__evalWithAutoGlobals(code, "script");
                if (!ok) throw new Error("script eval failed");
                if (String(url).indexOf("FeiLin") >= 0 || String(url).indexOf("feilin") >= 0) g.__feilinReady = true;
                if (String(url).indexOf("dynamicJS") >= 0) g.__dynamicJsReady = true;
                g.__logProgress("script-eval ok " + url.slice(0, 80));
                if (typeof self.onload === "function") self.onload();
                self.dispatchEvent(new g.Event("load"));
              } catch (e) {
                g.__logProgress("script-eval err " + (e && (e.message || e)));
                if (typeof self.onerror === "function") self.onerror(e);
                self.dispatchEvent(new g.Event("error"));
              }
            }, function (err) {
              g.__logProgress("script-load err " + (err && (err.message || err)));
              if (typeof self.onerror === "function") self.onerror(err);
              self.dispatchEvent(new g.Event("error"));
            });
          }
        },
        configurable: true
      });
    }
    if (lower === "audio") {
      var audio = el;
      audio.play = function () { return Promise.resolve(); };
      audio.pause = function () {};
      audio.load = function () {};
      audio.canPlayType = function () { return ""; };
      audio.addEventListener = El.prototype.addEventListener;
      audio.removeEventListener = El.prototype.removeEventListener;
      audio.dispatchEvent = El.prototype.dispatchEvent;
      return audio;
    }
    if (lower === "img" || lower === "image") {
      var img = el;
      Object.defineProperty(img, "src", {
        get: function () { return img._src || ""; },
        set: function (v) {
          img._src = v;
          img.attributes.src = v;
          // Synthetic success: captcha only needs onload for layout progression.
          g.setTimeout(function () {
            img.width = img.width || 320;
            img.height = img.height || 160;
            img.naturalWidth = img.width;
            img.naturalHeight = img.height;
            img.complete = true;
            if (typeof img.onload === "function") {
              try { img.onload(); } catch (e) {}
            }
            if (img.dispatchEvent) img.dispatchEvent(new g.Event("load"));
          }, 0);
        },
        configurable: true
      });
      img.complete = false;
      img.naturalWidth = 0;
      img.naturalHeight = 0;
      img.addEventListener = El.prototype.addEventListener;
      img.removeEventListener = El.prototype.removeEventListener;
      img.dispatchEvent = El.prototype.dispatchEvent;
    }
    return el;
  };
  g.document.createElementNS = function (ns, tag) { return g.document.createElement(tag); };
  g.document.createTextNode = function (t) { var n = new El("#text"); n.textContent = t; n.nodeType = 3; return n; };
  g.document.createDocumentFragment = function () { return new El("fragment"); };
  g.document.getElementById = function (id) { return g.document.__byId[id] || null; };
  g.document.getElementsByTagName = function (tag) {
    var t = String(tag).toUpperCase();
    var out = [];
    function walk(n) {
      if (n.tagName === t) out.push(n);
      if (n.children) for (var i = 0; i < n.children.length; i++) walk(n.children[i]);
    }
    walk(g.document.documentElement);
    return out;
  };
  g.document.getElementsByClassName = function () { return []; };
  function walkAll(n, fn) {
    fn(n);
    if (n.children) for (var i = 0; i < n.children.length; i++) walkAll(n.children[i], fn);
  }
  g.document.querySelector = function (sel) {
    if (!sel) return null;
    sel = String(sel).trim();
    if (sel.charAt(0) === "#") return g.document.getElementById(sel.slice(1));
    if (sel === "body") return g.document.body;
    if (sel === "head") return g.document.head;
    if (sel === "html") return g.document.documentElement;
    if (sel.charAt(0) === ".") {
      var cls = sel.slice(1).split(/[.\s[]/)[0];
      var found = null;
      walkAll(g.document.documentElement, function (n) {
        if (found || !n.className) return;
        var parts = String(n.className).split(/\s+/);
        if (parts.indexOf(cls) >= 0) found = n;
      });
      return found;
    }
    // tag or tag#id or #id remnants
    var hash = sel.indexOf("#");
    if (hash >= 0) return g.document.getElementById(sel.slice(hash + 1));
    var tag = sel.toUpperCase();
    var byTag = null;
    walkAll(g.document.documentElement, function (n) {
      if (!byTag && n.tagName === tag) byTag = n;
    });
    return byTag;
  };
  g.document.querySelectorAll = function (sel) {
    if (!sel) return [];
    sel = String(sel).trim();
    if (sel.charAt(0) === "#") {
      var one = g.document.getElementById(sel.slice(1));
      return one ? [one] : [];
    }
    if (sel.charAt(0) === ".") {
      var cls = sel.slice(1).split(/[.\s[]/)[0];
      var out = [];
      walkAll(g.document.documentElement, function (n) {
        if (!n.className) return;
        var parts = String(n.className).split(/\s+/);
        if (parts.indexOf(cls) >= 0) out.push(n);
      });
      return out;
    }
    var tag = sel.toUpperCase();
    var all = [];
    walkAll(g.document.documentElement, function (n) {
      if (n.tagName === tag) all.push(n);
    });
    return all;
  };
  g.document.addEventListener = function (type, fn) { g.document.body.addEventListener(type, fn); };
  g.document.removeEventListener = function (type, fn) { g.document.body.removeEventListener(type, fn); };
  g.document.dispatchEvent = function (ev) { return g.document.body.dispatchEvent(ev); };
  g.document.write = function () {};
  g.document.writeln = function () {};

  // HTTP bridge
  function parseHeadersJson(s) {
    try { return JSON.parse(s || "{}"); } catch (e) { return {}; }
  }

  g.XMLHttpRequest = function () {
    this.readyState = 0;
    this.status = 0;
    this.statusText = "";
    this.responseText = "";
    this.response = null;
    this.responseType = "";
    this.responseURL = "";
    this.timeout = 0;
    this.withCredentials = false;
    this.onreadystatechange = null;
    this.onload = null;
    this.onerror = null;
    this.ontimeout = null;
    this.onloadend = null;
    this.upload = {
      addEventListener: function () {},
      removeEventListener: function () {},
      dispatchEvent: function () { return true; }
    };
    this._method = "GET";
    this._url = "";
    this._headers = {};
    this._responseHeaders = {};
    this._listeners = {};
  };
  g.XMLHttpRequest.prototype.addEventListener = function (type, fn) {
    if (!this._listeners[type]) this._listeners[type] = [];
    this._listeners[type].push(fn);
  };
  g.XMLHttpRequest.prototype.removeEventListener = function (type, fn) {
    var arr = this._listeners[type];
    if (!arr) return;
    var i = arr.indexOf(fn);
    if (i >= 0) arr.splice(i, 1);
  };
  g.XMLHttpRequest.prototype.dispatchEvent = function (ev) {
    if (!ev) return false;
    if (!ev.target) ev.target = this;
    var type = ev.type;
    var arr = (this._listeners[type] || []).slice();
    for (var i = 0; i < arr.length; i++) {
      try { arr[i].call(this, ev); } catch (e) {}
    }
    var prop = "on" + type;
    if (typeof this[prop] === "function") {
      try { this[prop](ev); } catch (e) {}
    }
    return true;
  };
  g.XMLHttpRequest.prototype.open = function (method, url) {
    this._method = method;
    this._url = url;
    this.readyState = 1;
    this.dispatchEvent(new g.Event("readystatechange"));
  };
  g.XMLHttpRequest.prototype.setRequestHeader = function (k, v) { this._headers[k] = v; };
  g.XMLHttpRequest.prototype.getResponseHeader = function (k) {
    if (!k) return null;
    var key = String(k).toLowerCase();
    return this._responseHeaders[key] != null ? this._responseHeaders[key] : null;
  };
  g.XMLHttpRequest.prototype.getAllResponseHeaders = function () {
    var parts = [];
    for (var k in this._responseHeaders) {
      if (Object.prototype.hasOwnProperty.call(this._responseHeaders, k)) parts.push(k + ": " + this._responseHeaders[k]);
    }
    return parts.join("\r\n");
  };
  g.XMLHttpRequest.prototype.abort = function () {};
  g.XMLHttpRequest.prototype.send = function (body) {
    var self = this;
    var bridge = g.__javaHttp;
    if (!bridge) {
      self.readyState = 4;
      self.status = 0;
      self.dispatchEvent(new g.Event("error"));
      return;
    }
    var url = self._url;
    // Resolve relative URLs against page origin (SDK sometimes uses path-only URLs).
    try {
      if (url && url.charAt(0) === "/") {
        url = (g.location && g.location.origin ? g.location.origin : "https://uc.perfect99.com") + url;
      } else if (url && url.indexOf("http") !== 0 && url.indexOf("//") !== 0) {
        url = "https://uc.perfect99.com/" + String(url).replace(/^\.\//, "");
      } else if (url && url.indexOf("//") === 0) {
        url = "https:" + url;
      }
      self._url = url;
    } catch (e) {}
    g.__logProgress("xhr " + self._method + " " + String(url).slice(0, 120));
    // capture verify request fields for Java
    try {
      if (body && self._url && String(self._url).indexOf("verify") >= 0) {
        g.__captureVerifyRequest(String(body));
      }
    } catch (e) {}
    bridge.request(self._method, self._url, body == null ? "" : String(body), self._headers, function (status, text, headersJson) {
      // Deliver on a macrotask so subsequent SDK setTimeout chains can run under host drain.
      g.setTimeout(function () {
        g.__logProgress("xhr-done " + (status | 0) + " " + String(self._url).slice(0, 100) + " len=" + ((text || "").length));
        if (String(self._url).indexOf("captcha-open") >= 0 && String(self._url).indexOf("verify") < 0) {
          g.__logProgress("xhr-body " + String(text || "").slice(0, 220));
          // SDK should load dynamicJS from StaticPath; under polyfill this often does not fire.
          // Node helper traffic shows: https://g.alicdn.com/captcha-frontend/dynamicJS/{StaticPath}.js
          try {
            var initJson = JSON.parse(text || "{}");
            if (initJson && initJson.Code === "Success" && initJson.StaticPath) {
              if (initJson.CertifyId) g.__captured.certifyId = initJson.CertifyId;
              g.__initStaticPath = initJson.StaticPath;
              var dynUrl = "https://g.alicdn.com/captcha-frontend/dynamicJS/" + initJson.StaticPath + ".js";
              if (!g.__loadedDynamicPaths) g.__loadedDynamicPaths = {};
              if (!g.__loadedDynamicPaths[dynUrl]) {
                g.__loadedDynamicPaths[dynUrl] = true;
                g.__logProgress("autoload dynamicJS " + dynUrl);
                g.__loadScript(dynUrl, function (code) {
                  try {
                    var ok = g.__evalWithAutoGlobals(code, "dynamicJS");
                    if (ok) {
                      g.__dynamicJsReady = true;
                      g.__logProgress("dynamicJS eval ok len=" + (code || "").length);
                    } else {
                      g.__logProgress("dynamicJS eval failed after auto-globals");
                    }
                  } catch (eEval) {
                    g.__logProgress("dynamicJS eval err " + (eEval && (eEval.message || eEval)));
                  }
                }, function (eLoad) {
                  g.__logProgress("dynamicJS load err " + (eLoad && (eLoad.message || eLoad)));
                });
              }
            }
          } catch (eParse) {
            g.__logProgress("init json parse err");
          }
        }
        self.status = status | 0;
        self.statusText = status ? "OK" : "ERR";
        self.responseText = text || "";
        // Honor responseType=json when SDK expects parsed object.
        if (String(self.responseType).toLowerCase() === "json") {
          try { self.response = JSON.parse(text || "null"); }
          catch (e) { self.response = null; }
        } else {
          self.response = self.responseText;
        }
        self.responseURL = self._url;
        self._responseHeaders = parseHeadersJson(headersJson);
        try {
          if (self._url && String(self._url).indexOf("verify") >= 0) {
            g.__captureVerifyResponse(self.responseText);
          }
        } catch (e) {}
        try {
          self.readyState = 2;
          self.dispatchEvent(new g.Event("readystatechange"));
          self.readyState = 3;
          self.dispatchEvent(new g.Event("readystatechange"));
          self.readyState = 4;
          self.dispatchEvent(new g.Event("readystatechange"));
          if (status > 0) {
            self.dispatchEvent(new g.Event("load"));
          } else {
            self.dispatchEvent(new g.Event("error"));
          }
          self.dispatchEvent(new g.Event("loadend"));
        } catch (e2) {
          g.__logProgress("xhr event err " + (e2 && (e2.message || e2)));
        }
      }, 0);
    });
  };

  g.fetch = function (url, opts) {
    opts = opts || {};
    return new Promise(function (resolve, reject) {
      var bridge = g.__javaHttp;
      if (!bridge) {
        reject(new Error("no java http"));
        return;
      }
      var method = opts.method || "GET";
      var headers = opts.headers || {};
      var body = opts.body == null ? "" : String(opts.body);
      bridge.request(method, String(url), body, headers, function (status, text) {
        resolve({
          ok: status >= 200 && status < 300,
          status: status,
          text: function () { return Promise.resolve(text || ""); },
          json: function () {
            try { return Promise.resolve(JSON.parse(text || "null")); }
            catch (e) { return Promise.reject(e); }
          },
          headers: { get: function () { return null; } }
        });
      });
    });
  };

  g.__loadScript = function (url, onOk, onErr) {
    var bridge = g.__javaHttp;
    if (!bridge) {
      if (onErr) onErr(new Error("no java http"));
      return;
    }
    try {
      if (url && url.charAt(0) === "/") {
        url = (g.location && g.location.origin ? g.location.origin : "https://uc.perfect99.com") + url;
      } else if (url && url.indexOf("//") === 0) {
        url = "https:" + url;
      }
    } catch (e) {}
    g.__logProgress("script " + String(url).slice(0, 120));
    bridge.request("GET", url, "", {}, function (status, text) {
      g.__logProgress("script-done " + (status | 0) + " " + String(url).slice(0, 100) + " len=" + ((text || "").length));
      if (status >= 200 && status < 300) onOk(text || "");
      else if (onErr) onErr(new Error("script status " + status));
    });
  };

  // Capture hooks filled by Java orchestration helpers
  g.__cvp = null;
  g.__captured = { data: null, deviceToken: null, certifyId: null, securityToken: null, verifyResult: null };

  g.__captureVerifyRequest = function (body) {
    try {
      var decoded = decodeURIComponent(body);
      var parts = decoded.split("&");
      for (var i = 0; i < parts.length; i++) {
        var p = parts[i];
        var idx = p.indexOf("=");
        if (idx < 0) continue;
        var key = p.substring(0, idx);
        var val = p.substring(idx + 1);
        if (key === "CaptchaVerifyParam") {
          try {
            var cvp = JSON.parse(val);
            g.__captured.data = cvp.data || g.__captured.data;
            g.__captured.deviceToken = cvp.deviceToken || g.__captured.deviceToken;
            g.__captured.certifyId = cvp.certifyId || g.__captured.certifyId;
          } catch (e1) {}
        }
        if (key === "CertifyId" && !g.__captured.certifyId) g.__captured.certifyId = val;
        if (key === "data" && !g.__captured.data) g.__captured.data = val;
        if (key === "deviceToken" && !g.__captured.deviceToken) g.__captured.deviceToken = val;
      }
      // also try raw JSON body
      if (!g.__captured.data) {
        try {
          var j = JSON.parse(body);
          if (j.data) g.__captured.data = j.data;
          if (j.deviceToken) g.__captured.deviceToken = j.deviceToken;
          if (j.certifyId) g.__captured.certifyId = j.certifyId;
        } catch (e2) {}
      }
    } catch (e) {}
  };

  g.__captureVerifyResponse = function (text) {
    try {
      var resp = JSON.parse(text);
      if (resp.Result) {
        if (resp.Result.securityToken) g.__captured.securityToken = resp.Result.securityToken;
        if (resp.Result.VerifyResult != null) g.__captured.verifyResult = resp.Result.VerifyResult;
      }
    } catch (e) {}
  };

  g.__getDeviceTokenNow = function () {
    try {
      if (g.z_um && typeof g.z_um.getToken === "function") return g.z_um.getToken();
    } catch (e) {}
    try {
      if (g.um && typeof g.um.getToken === "function") return g.um.getToken();
    } catch (e) {}
    return g.__captured.deviceToken || null;
  };

  g.__sleep = function (ms) {
    return new Promise(function (resolve) { setTimeout(resolve, ms); });
  };

  g.__simulateDrag = async function () {
    var slider = g.document.getElementById("aliyunCaptcha-sliding-slider");
    var body = g.document.getElementById("aliyunCaptcha-sliding-body");
    if (!slider) return false;
    // Derive distance from DOM rects (same as feilin_helper.js), not fixed 120→440.
    var sr = slider.getBoundingClientRect ? slider.getBoundingClientRect() : { x: 100, y: 100, width: 40, height: 40 };
    var br = body && body.getBoundingClientRect
      ? body.getBoundingClientRect()
      : { x: 100, y: 100, width: 360, height: 40 };
    var startX = sr.x + sr.width / 2;
    var startY = sr.y + sr.height / 2;
    var endX = br.x + br.width - sr.width / 2;
    var endY = br.y + br.height / 2;
    var distance = endX - startX;
    if (!(distance > 0)) {
      // Fallback only if layout stubs missing entirely
      startX = 120; startY = 120; endX = 440; endY = 120; distance = endX - startX;
    }
    slider.dispatchEvent(new g.MouseEvent("mousedown", { bubbles: true, cancelable: true, clientX: startX, clientY: startY, button: 0, buttons: 1 }));
    await g.__sleep(80 + Math.random() * 120);
    var totalSteps = 80 + Math.floor(Math.random() * 20);
    for (var i = 1; i <= totalSteps; i++) {
      var t = i / totalSteps;
      var eased = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
      var x = startX + distance * eased + (Math.random() - 0.5) * 2;
      var y = startY + (Math.random() - 0.5) * 3 + Math.sin(t * Math.PI * 2) * 2;
      g.document.dispatchEvent(new g.MouseEvent("mousemove", { bubbles: true, cancelable: true, clientX: x, clientY: y, button: 0, buttons: 1 }));
      await g.__sleep(5 + Math.random() * 15 + (t < 0.1 || t > 0.9 ? 20 : 0));
    }
    await g.__sleep(50 + Math.random() * 100);
    g.document.dispatchEvent(new g.MouseEvent("mouseup", { bubbles: true, cancelable: true, clientX: endX, clientY: endY, button: 0, buttons: 0 }));
    for (var j = 0; j < 15; j++) {
      await g.__sleep(1000);
      if (g.__cvp || g.__captured.data) break;
    }
    return true;
  };

  g.__waitForSlider = async function (maxMs) {
    var deadline = Date.now() + (maxMs || 20000);
    while (Date.now() < deadline) {
      var slider = g.document.getElementById("aliyunCaptcha-sliding-slider");
      if (slider) return slider;
      // some builds use querySelector
      slider = g.document.querySelector("#aliyunCaptcha-sliding-slider");
      if (slider) return slider;
      await g.__sleep(200);
    }
    return null;
  };

  // Host polls __solveBox (Promise.then is unreliable under Graal host pump).
  g.__solveBox = { done: false, val: null, err: null };
  g.__solveCaptcha = function (prefix, sceneId) {
    g.__solveBox = { done: false, val: null, err: null };
    g.__captured = { data: null, deviceToken: null, certifyId: null, securityToken: null, verifyResult: null };
    g.__cvp = null;
    g.__progress = [];
    g.__logProgress("solve start prefix=" + prefix + " scene=" + sceneId);
    g.__logProgress("initAliyunCaptcha type=" + typeof g.initAliyunCaptcha);
    g.__dynamicJsReady = false;
    g.__feilinReady = false;
    try {
      var nc = g.document.getElementById("nc");
      if (nc) {
        // Drop any previous stub widget so SDK can mount cleanly.
        nc.innerHTML = "";
      }
    } catch (eClear) {}

    function finish() {
      var token = g.__getDeviceTokenNow();
      if (!g.__captured.deviceToken) g.__captured.deviceToken = token;
      g.__logProgress("tokenLen=" + (token ? String(token).length : 0)
        + " certifyId=" + (g.__captured.certifyId || ""));
      g.__solveBox.val = {
        data: g.__captured.data,
        deviceToken: g.__captured.deviceToken || token,
        certifyId: g.__captured.certifyId,
        securityToken: g.__captured.securityToken,
        verifyResult: g.__captured.verifyResult,
        cvp: g.__cvp || null,
        progress: (g.__progress || []).join(" | ")
      };
      g.__solveBox.done = true;
    }

    function afterDrag() {
      g.__logProgress("drag done captured.data=" + !!(g.__captured && g.__captured.data)
        + " securityToken=" + !!(g.__captured && g.__captured.securityToken)
        + " cvp=" + !!g.__cvp);
      finish();
    }

    function runDragIfSlider() {
      var slider = g.document.getElementById("aliyunCaptcha-sliding-slider")
        || g.document.querySelector("#aliyunCaptcha-sliding-slider");
      g.__logProgress("slider=" + (!!slider) + " ids=" + Object.keys(g.document.__byId || {}).slice(0, 30).join(","));
      if (!slider) {
        g.__logProgress("no slider mounted; skip drag");
        finish();
        return;
      }
      g.__simulateDragMacrotask(afterDrag);
    }

    function waitSlider(remainingMs) {
      var slider = g.document.getElementById("aliyunCaptcha-sliding-slider")
        || g.document.querySelector("#aliyunCaptcha-sliding-slider");
      if (slider) {
        runDragIfSlider();
        return;
      }
      if (remainingMs <= 0) {
        g.__logProgress("slider wait exhausted");
        runDragIfSlider();
        return;
      }
      g.setTimeout(function () { waitSlider(remainingMs - 200); }, 200);
    }

    try {
      var initRet = g.initAliyunCaptcha({
        prefix: prefix,
        SceneId: sceneId,
        mode: "embed",
        element: "#nc",
        success: function (param) {
          g.__cvp = param;
          g.__logProgress("success callback");
        },
        fail: function (err) {
          g.__logProgress("fail callback " + (err && (err.message || err)));
        },
        getInstance: function (inst) {
          g.__captchaInstance = inst;
          g.__logProgress("getInstance");
        }
      });
      g.__logProgress("init returned type=" + typeof initRet
        + " thenable=" + !!(initRet && typeof initRet.then === "function"));
    } catch (e) {
      g.__logProgress("init throw " + (e && (e.message || e)));
    }

    // Wait for Init + dynamicJS/FeiLin, then drag.
    function waitReady(remainingMs) {
      var dyn = !!g.__dynamicJsReady;
      var fei = !!g.__feilinReady;
      var nc = g.document.getElementById("nc");
      var childCount = nc && nc.children ? nc.children.length : 0;
      var slider = g.document.getElementById("aliyunCaptcha-sliding-slider");
      var hasListener = !!(slider && slider._listeners && (
        (slider._listeners.mousedown||[]).length +
        (slider._listeners.pointerdown||[]).length +
        (slider._listeners.touchstart||[]).length +
        (slider._listeners.click||[]).length
      ));
      // Real mount: children under #nc and preferably listeners on slider.
      if (dyn && fei && childCount > 0 && (hasListener || remainingMs < 8000)) {
        g.__logProgress("ready mount children=" + childCount + " listener=" + hasListener
          + " certifyId=" + (g.__captured.certifyId || ""));
        g.setTimeout(function () { waitSlider(30000); }, hasListener ? 800 : 2000);
        return;
      }
      // Scripts ready but widget not mounted: re-call init once to bind after dynamicJS/FeiLin.
      if (dyn && fei && !g.__reinitTried && remainingMs < 20000) {
        g.__reinitTried = true;
        g.__logProgress("reinit after scripts children=" + childCount);
        try {
          g.initAliyunCaptcha({
            prefix: prefix,
            SceneId: sceneId,
            mode: "embed",
            element: "#nc",
            success: function (param) { g.__cvp = param; g.__logProgress("success callback reinit"); },
            fail: function (err) { g.__logProgress("fail callback reinit " + (err && (err.message || err))); },
            getInstance: function (inst) { g.__captchaInstance = inst; g.__logProgress("getInstance reinit"); }
          });
        } catch (eRe) {
          g.__logProgress("reinit throw " + (eRe && (eRe.message || eRe)));
        }
      }
      if (remainingMs <= 0) {
        g.__logProgress("ready wait exhausted children=" + childCount + " dyn=" + dyn + " fei=" + fei);
        // Last resort stub mount so drag path can execute and surface clearer failures.
        g.__ensureCaptchaDom(nc || g.document.body);
        waitSlider(3000);
        return;
      }
      if (remainingMs % 2000 < 200) {
        g.__logProgress("wait mount children=" + childCount + " dyn=" + dyn + " fei=" + fei + " left=" + remainingMs);
      }
      g.setTimeout(function () { waitReady(remainingMs - 200); }, 200);
    }
    g.setTimeout(function () { waitReady(30000); }, 500);
    // Return a thenable only for API compatibility; Java prefers __solveBox.
    return {
      then: function (onFulfilled, onRejected) {
        function poll() {
          if (g.__solveBox.done) {
            try {
              if (g.__solveBox.err) {
                if (onRejected) onRejected(g.__solveBox.err);
              } else if (onFulfilled) {
                onFulfilled(g.__solveBox.val);
              }
            } catch (e) {
              if (onRejected) onRejected(e);
            }
            return;
          }
          g.setTimeout(poll, 20);
        }
        g.setTimeout(poll, 0);
      }
    };
  };

  // Macrotask drag: same motion as async __simulateDrag but only setTimeout steps.
  g.__simulateDragMacrotask = function (done) {
    var slider = g.document.getElementById("aliyunCaptcha-sliding-slider");
    var body = g.document.getElementById("aliyunCaptcha-sliding-body");
    if (!slider) {
      if (done) done(false);
      return;
    }
    var sr = slider.getBoundingClientRect ? slider.getBoundingClientRect() : { x: 100, y: 100, width: 40, height: 40 };
    var br = body && body.getBoundingClientRect
      ? body.getBoundingClientRect()
      : { x: 100, y: 100, width: 360, height: 40 };
    var startX = sr.x + sr.width / 2;
    var startY = sr.y + sr.height / 2;
    var endX = br.x + br.width - sr.width / 2;
    var endY = br.y + br.height / 2;
    var distance = endX - startX;
    if (!(distance > 0)) {
      startX = 120; startY = 120; endX = 440; endY = 120; distance = endX - startX;
    }
    slider.dispatchEvent(new g.MouseEvent("mousedown", {
      bubbles: true, cancelable: true, clientX: startX, clientY: startY, button: 0, buttons: 1
    }));
    var totalSteps = 80 + Math.floor(Math.random() * 20);
    var i = 1;
    function step() {
      if (i <= totalSteps) {
        var t = i / totalSteps;
        var eased = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
        var x = startX + distance * eased + (Math.random() - 0.5) * 2;
        var y = startY + (Math.random() - 0.5) * 3 + Math.sin(t * Math.PI * 2) * 2;
        g.document.dispatchEvent(new g.MouseEvent("mousemove", {
          bubbles: true, cancelable: true, clientX: x, clientY: y, button: 0, buttons: 1
        }));
        i++;
        g.setTimeout(step, 5 + Math.random() * 15 + (t < 0.1 || t > 0.9 ? 20 : 0));
        return;
      }
      g.document.dispatchEvent(new g.MouseEvent("mouseup", {
        bubbles: true, cancelable: true, clientX: endX, clientY: endY, button: 0, buttons: 0
      }));
      var waits = 0;
      function waitResult() {
        if (g.__cvp || (g.__captured && (g.__captured.data || g.__captured.securityToken)) || waits >= 40) {
          if (done) done(true);
          return;
        }
        waits++;
        g.setTimeout(waitResult, 500);
      }
      g.setTimeout(waitResult, 80);
    }
    g.setTimeout(step, 80 + Math.random() * 120);
  };

  g.__initAndToken = function (prefix, sceneId) {
    return new Promise(function (resolve) {
      try {
        g.initAliyunCaptcha({
          prefix: prefix,
          SceneId: sceneId,
          mode: "embed",
          element: "#nc",
          success: function () {},
          fail: function () {},
          getInstance: function () {}
        });
      } catch (e) {}
      g.setTimeout(function () {
        resolve(g.__getDeviceTokenNow());
      }, 8000);
    });
  };

})(typeof globalThis !== "undefined" ? globalThis : this);
