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

  g.requestAnimationFrame = g.requestAnimationFrame || function (cb) { return setTimeout(function () { cb(Date.now()); }, 16); };
  g.cancelAnimationFrame = g.cancelAnimationFrame || function (id) { clearTimeout(id); };

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

  // Tiny DOM tree for captcha mount point
  function El(tag) {
    this.tagName = String(tag || "div").toUpperCase();
    this.id = "";
    this.className = "";
    this.style = {};
    this.children = [];
    this.parentNode = null;
    this.ownerDocument = g.document;
    this.attributes = {};
    this._listeners = {};
    this.innerHTML = "";
    this.textContent = "";
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
    if (k === "id") this.id = String(v);
    if (k === "class") this.className = String(v);
    if (k === "src") this.src = String(v);
    if (k === "href") this.href = String(v);
    if (k === "type") this.type = String(v);
  };
  El.prototype.getAttribute = function (k) {
    if (k === "id") return this.id || null;
    if (k === "class") return this.className || null;
    if (k === "src") return this.src || null;
    return this.attributes[k] != null ? this.attributes[k] : null;
  };
  El.prototype.removeAttribute = function (k) { delete this.attributes[k]; if (k === "id") this.id = ""; };
  El.prototype.appendChild = function (c) {
    c.parentNode = this;
    this.children.push(c);
    if (c.id) g.document.__byId[c.id] = c;
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
  g.EventTarget = El;

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
    if (String(tag).toLowerCase() === "script") {
      var self = el;
      Object.defineProperty(el, "src", {
        get: function () { return self._src || ""; },
        set: function (v) {
          self._src = v;
          self.attributes.src = v;
          if (v && String(v).indexOf("http") === 0) {
            // async load remote script via Java HTTP
            g.__loadScript(String(v), function (code) {
              try {
                g.eval(code);
                if (typeof self.onload === "function") self.onload();
              } catch (e) {
                if (typeof self.onerror === "function") self.onerror(e);
              }
            }, function (err) {
              if (typeof self.onerror === "function") self.onerror(err);
            });
          }
        },
        configurable: true
      });
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
  g.document.querySelector = function (sel) {
    if (!sel) return null;
    if (sel.charAt(0) === "#") return g.document.getElementById(sel.slice(1));
    if (sel === "body") return g.document.body;
    if (sel === "head") return g.document.head;
    if (sel === "html") return g.document.documentElement;
    if (sel.indexOf("#") === 0) return g.document.getElementById(sel.slice(1));
    return g.document.getElementById(sel.replace(/^#/, "")) || null;
  };
  g.document.querySelectorAll = function (sel) {
    var one = g.document.querySelector(sel);
    return one ? [one] : [];
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
    this.responseType = "text";
    this.responseURL = "";
    this.timeout = 0;
    this.withCredentials = false;
    this.onreadystatechange = null;
    this.onload = null;
    this.onerror = null;
    this.ontimeout = null;
    this.upload = { addEventListener: function () {} };
    this._method = "GET";
    this._url = "";
    this._headers = {};
    this._responseHeaders = {};
  };
  g.XMLHttpRequest.prototype.open = function (method, url) {
    this._method = method;
    this._url = url;
    this.readyState = 1;
    if (this.onreadystatechange) this.onreadystatechange();
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
      if (self.onerror) self.onerror(new Error("no java http"));
      return;
    }
    // capture verify request fields for Java
    try {
      if (body && self._url && String(self._url).indexOf("verify") >= 0) {
        g.__captureVerifyRequest(String(body));
      }
    } catch (e) {}
    bridge.request(self._method, self._url, body == null ? "" : String(body), self._headers, function (status, text, headersJson) {
      self.readyState = 4;
      self.status = status | 0;
      self.statusText = status ? "OK" : "ERR";
      self.responseText = text || "";
      self.response = self.responseText;
      self.responseURL = self._url;
      self._responseHeaders = parseHeadersJson(headersJson);
      try {
        if (self._url && String(self._url).indexOf("verify") >= 0) {
          g.__captureVerifyResponse(self.responseText);
        }
      } catch (e) {}
      if (self.onreadystatechange) self.onreadystatechange();
      if (status > 0) {
        if (self.onload) self.onload();
      } else {
        if (self.onerror) self.onerror(new Error("http status 0"));
      }
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
    bridge.request("GET", url, "", {}, function (status, text) {
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
    if (!slider) return false;
    var startX = 120, startY = 120, endX = 440, endY = 120, distance = endX - startX;
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

  g.__solveCaptcha = async function (prefix, sceneId) {
    g.__captured = { data: null, deviceToken: null, certifyId: null, securityToken: null, verifyResult: null };
    g.__cvp = null;
    try {
      await g.initAliyunCaptcha({
        prefix: prefix,
        SceneId: sceneId,
        mode: "embed",
        element: "#nc",
        success: function (param) { g.__cvp = param; },
        fail: function () {},
        getInstance: function () {}
      });
    } catch (e) {}
    await g.__sleep(8000);
    await g.__simulateDrag();
    var token = g.__getDeviceTokenNow();
    if (!g.__captured.deviceToken) g.__captured.deviceToken = token;
    return {
      data: g.__captured.data,
      deviceToken: g.__captured.deviceToken || token,
      certifyId: g.__captured.certifyId,
      securityToken: g.__captured.securityToken,
      verifyResult: g.__captured.verifyResult,
      cvp: g.__cvp || null
    };
  };

  g.__initAndToken = async function (prefix, sceneId) {
    try {
      await g.initAliyunCaptcha({
        prefix: prefix,
        SceneId: sceneId,
        mode: "embed",
        element: "#nc",
        success: function () {},
        fail: function () {},
        getInstance: function () {}
      });
    } catch (e) {}
    await g.__sleep(8000);
    return g.__getDeviceTokenNow();
  };

})(typeof globalThis !== "undefined" ? globalThis : this);
