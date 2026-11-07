/* ==========================================================================
   CronoBus - Motor de mapa offline compatible con la API pública de Leaflet
   ==========================================================================
   Implementa el subconjunto de Leaflet 1.9.4 que usa la SPA cuando no hay red:

     L.map(id, opciones)        L.tileLayer(url, opciones)
     L.polyline(latlngs, opc)   L.circle(latlng, opciones)
     L.marker(latlng, opciones) L.divIcon(opciones)
     L.control.zoom(opciones)   L.latLng(lat, lng)

   Características:
   - Proyección Web Mercator (EPSG:3857) y zoom entero 3..19.
   - Arrastre con puntero, rueda del ratón y doble toque para acercar.
   - Retícula vectorial dibujada en canvas: cero peticiones de red, cero CDN.
   - Polilíneas/círculos en SVG y marcadores DOM con popups Classic Signal.
   - Compatible con el modo nocturno (`body.dark-mode`) y con `invalidateSize()`.

   El motor NO descarga mosaicos ni evalúa la URL de `L.tileLayer`: la recibe por
   compatibilidad con el código existente y la ignora deliberadamente.
   ========================================================================== */
(function (global) {
  'use strict';

  // Idempotente: si el asset se carga dos veces, no se redefine el motor.
  if (global.L && global.L.__cronobusOffline) return;

  var SVG_NS = 'http://www.w3.org/2000/svg';
  var TILE_SIZE = 256;
  var METERS_PER_DEGREE = 111320;
  var MIN_ZOOM = 3;
  var MAX_ZOOM = 19;
  var DEFAULT_CENTER = { lat: 19.4245, lng: -99.1750 };
  var DEFAULT_ZOOM = 15;

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  function isFiniteNumber(value) {
    return typeof value === 'number' && isFinite(value);
  }

  function toLatLng(value) {
    if (value == null) return null;

    if (Array.isArray(value) && value.length >= 2) {
      var arrayLat = Number(value[0]);
      var arrayLng = Number(value[1]);
      if (isFiniteNumber(arrayLat) && isFiniteNumber(arrayLng)) {
        return { lat: arrayLat, lng: arrayLng };
      }
      return null;
    }

    var lat = Number(value.lat != null ? value.lat : value.latitude);
    var lng = Number(value.lng != null ? value.lng : value.longitude);
    if (isFiniteNumber(lat) && isFiniteNumber(lng)) {
      return { lat: lat, lng: lng };
    }
    return null;
  }

  function normalizeLatLngs(input) {
    if (!Array.isArray(input)) {
      var single = toLatLng(input);
      return single ? [single] : [];
    }
    var result = [];
    for (var i = 0; i < input.length; i += 1) {
      var item = input[i];
      if (Array.isArray(item) && item.length && typeof item[0] !== 'number') {
        // Anidamiento (polilínea multisegmento): se aplana en orden.
        result = result.concat(normalizeLatLngs(item));
      } else {
        var ll = toLatLng(item);
        if (ll) result.push(ll);
      }
    }
    return result;
  }

  function clampZoom(zoom) {
    var numeric = Number(zoom);
    if (!isFiniteNumber(numeric)) return DEFAULT_ZOOM;
    return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.round(numeric)));
  }

  function project(latlng, zoom) {
    var degrees = Math.PI / 180;
    var sin = Math.sin(latlng.lat * degrees);
    var max = 1 - 1e-15;
    sin = Math.max(Math.min(sin, max), -max);
    var scale = TILE_SIZE * Math.pow(2, zoom);
    return {
      x: scale * (0.5 + latlng.lng / 360),
      y: scale * (0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)),
    };
  }

  function unproject(point, zoom) {
    var scale = TILE_SIZE * Math.pow(2, zoom);
    var lng = (point.x / scale - 0.5) * 360;
    var n = Math.PI - (2 * Math.PI * point.y) / scale;
    var lat = (180 / Math.PI) * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)));
    return { lat: lat, lng: lng };
  }

  function metersPerPixel(lat, zoom) {
    return (156543.03392 * Math.cos((lat * Math.PI) / 180)) / Math.pow(2, zoom);
  }

  function niceStep(value) {
    var safe = Math.max(1e-6, value);
    var exponent = Math.floor(Math.log(safe) / Math.LN10);
    var magnitude = Math.pow(10, exponent);
    var normalized = safe / magnitude;
    var nice = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
    return nice * magnitude;
  }

  function setStyles(element, styles) {
    for (var key in styles) {
      if (Object.prototype.hasOwnProperty.call(styles, key)) {
        element.style[key] = styles[key];
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Emisor de eventos mínimo (on / off / fire)
  // ---------------------------------------------------------------------------

  function Evented() {
    this._events = {};
  }

  Evented.prototype.on = function (type, handler, context) {
    if (!type || typeof handler !== 'function') return this;
    var list = this._events[type] || (this._events[type] = []);
    list.push({ handler: handler, context: context });
    return this;
  };

  Evented.prototype.off = function (type, handler) {
    if (!type) {
      this._events = {};
      return this;
    }
    var list = this._events[type];
    if (!list) return this;
    if (!handler) {
      delete this._events[type];
      return this;
    }
    for (var i = list.length - 1; i >= 0; i -= 1) {
      if (list[i].handler === handler) list.splice(i, 1);
    }
    return this;
  };

  Evented.prototype.fire = function (type, data) {
    var list = this._events[type];
    if (!list || !list.length) return this;
    var payload = data || {};
    payload.type = type;
    payload.target = this;
    for (var i = 0; i < list.length; i += 1) {
      try {
        list[i].handler.call(list[i].context || this, payload);
      } catch (failure) {
        // Un listener roto no debe tumbar el mapa completo.
        if (global.console && global.console.error) {
          global.console.error('CronoBus offline map listener error:', failure);
        }
      }
    }
    return this;
  };

  // ---------------------------------------------------------------------------
  // Capas base
  // ---------------------------------------------------------------------------

  function Layer() {
    Evented.call(this);
    this._map = null;
    this._popupContent = null;
  }

  Layer.prototype = Object.create(Evented.prototype);
  Layer.prototype.constructor = Layer;

  Layer.prototype.addTo = function (map) {
    map.addLayer(this);
    return this;
  };

  Layer.prototype.remove = function () {
    if (this._map) this._map.removeLayer(this);
    return this;
  };

  Layer.prototype.bindPopup = function (content) {
    this._popupContent = content;
    return this;
  };

  Layer.prototype.unbindPopup = function () {
    this._popupContent = null;
    if (this._map) this._map.closePopup();
    return this;
  };

  Layer.prototype.openPopup = function () {
    if (this._map) this._map._openPopup(this);
    return this;
  };

  Layer.prototype.closePopup = function () {
    if (this._map) this._map.closePopup();
    return this;
  };

  // ---------------------------------------------------------------------------
  // Mapa
  // ---------------------------------------------------------------------------

  function LeafletMap(container, options) {
    Evented.call(this);
    this.options = options || {};
    this._container = typeof container === 'string'
      ? global.document.getElementById(container)
      : container;

    if (!this._container) {
      throw new Error('L.map: no se encontró el contenedor del mapa.');
    }

    this._layers = [];
    this._panes = {};
    this._controlCorners = {};
    this._popup = null;
    this._popupSource = null;
    this._center = { lat: DEFAULT_CENTER.lat, lng: DEFAULT_CENTER.lng };
    this._zoom = DEFAULT_ZOOM;
    this._minZoom = this.options.minZoom != null ? clampZoom(this.options.minZoom) : MIN_ZOOM;
    this._maxZoom = this.options.maxZoom != null ? clampZoom(this.options.maxZoom) : MAX_ZOOM;
    this._size = { x: 0, y: 0 };

    this._container.classList.add('leaflet-container');
    this._container.style.touchAction = 'none';
    this._container.style.cursor = 'grab';

    this._createPanes();
    this._bindInteractions();
    this._measure();
    this._renderAll();

    if (this.options.zoomControl !== false) {
      new ZoomControl().addTo(this);
    }

    var self = this;
    if (global.requestAnimationFrame) {
      global.requestAnimationFrame(function () {
        self.invalidateSize();
      });
    }
  }

  LeafletMap.prototype = Object.create(Evented.prototype);
  LeafletMap.prototype.constructor = LeafletMap;

  LeafletMap.prototype._createPanes = function () {
    var definitions = [
      ['tilePane', 200],
      ['overlayPane', 400],
      ['shadowPane', 500],
      ['markerPane', 600],
      ['tooltipPane', 650],
      ['popupPane', 700],
    ];

    for (var i = 0; i < definitions.length; i += 1) {
      var name = definitions[i][0];
      var zIndex = definitions[i][1];
      var pane = global.document.createElement('div');
      pane.className = 'leaflet-pane leaflet-' + name;
      setStyles(pane, {
        position: 'absolute',
        left: '0',
        top: '0',
        width: '100%',
        height: '100%',
        zIndex: String(zIndex),
      });
      this._container.appendChild(pane);
      this._panes[name] = pane;
    }

    // El SVG comparte coordenadas de contenedor con los marcadores.
    this._svg = global.document.createElementNS(SVG_NS, 'svg');
    this._svg.setAttribute('class', 'leaflet-zoom-animated');
    this._svg.setAttribute('width', '100%');
    this._svg.setAttribute('height', '100%');
    setStyles(this._svg, { overflow: 'visible', pointerEvents: 'none' });
    this._panes.overlayPane.appendChild(this._svg);
  };

  LeafletMap.prototype._measure = function () {
    this._size.x = Math.max(0, this._container.clientWidth || 0);
    this._size.y = Math.max(0, this._container.clientHeight || 0);
  };

  LeafletMap.prototype._bindInteractions = function () {
    var map = this;
    var container = this._container;
    var dragging = false;
    var moved = false;
    var startX = 0;
    var startY = 0;
    var startCenterPixel = null;

    function isInteractiveTarget(target) {
      if (!target || !target.closest) return false;
      return !!target.closest('.leaflet-control, .leaflet-popup');
    }

    function localPoint(event) {
      var rect = container.getBoundingClientRect();
      return { x: event.clientX - rect.left, y: event.clientY - rect.top };
    }

    function onPointerDown(event) {
      if (event.button != null && event.button !== 0) return;
      if (isInteractiveTarget(event.target)) return;

      dragging = true;
      moved = false;
      startX = event.clientX;
      startY = event.clientY;
      startCenterPixel = project(map._center, map._zoom);
      container.style.cursor = 'grabbing';

      if (container.setPointerCapture && event.pointerId != null) {
        try {
          container.setPointerCapture(event.pointerId);
        } catch (ignored) {
          // Algunos WebView antiguos no permiten capturar el puntero.
        }
      }
      if (event.cancelable) event.preventDefault();
    }

    function onPointerMove(event) {
      if (!dragging) return;
      var dx = event.clientX - startX;
      var dy = event.clientY - startY;
      if (Math.abs(dx) > 3 || Math.abs(dy) > 3) moved = true;

      // Al arrastrar el contenido, el centro del mapa se desplaza en sentido contrario.
      var nextCenterPixel = {
        x: startCenterPixel.x - dx,
        y: startCenterPixel.y - dy,
      };
      map._center = unproject(nextCenterPixel, map._zoom);
      map._renderAll();
      if (event.cancelable) event.preventDefault();
    }

    function onPointerUp(event) {
      if (!dragging) return;
      dragging = false;
      container.style.cursor = 'grab';

      if (!moved) {
        var point = localPoint(event);
        map.fire('click', {
          latlng: map.containerPointToLatLng(point),
          containerPoint: point,
          originalEvent: event,
        });
      }
    }

    function onWheel(event) {
      if (event.cancelable) event.preventDefault();
      var point = localPoint(event);
      var anchorBefore = map.containerPointToLatLng(point);
      var nextZoom = clampZoom(map._zoom + (event.deltaY < 0 ? 1 : -1));
      if (nextZoom === map._zoom) return;

      map._zoom = nextZoom;
      var anchorPixel = project(anchorBefore, nextZoom);
      var centerPixel = {
        x: anchorPixel.x - (point.x - map._size.x / 2),
        y: anchorPixel.y - (point.y - map._size.y / 2),
      };
      map._center = unproject(centerPixel, nextZoom);
      map._renderAll();
      map.fire('zoomend');
    }

    function onDoubleClick(event) {
      if (event.cancelable) event.preventDefault();
      var point = localPoint(event);
      var anchorBefore = map.containerPointToLatLng(point);
      var nextZoom = clampZoom(map._zoom + 1);
      if (nextZoom === map._zoom) return;

      map._zoom = nextZoom;
      var anchorPixel = project(anchorBefore, nextZoom);
      map._center = unproject(
        {
          x: anchorPixel.x - (point.x - map._size.x / 2),
          y: anchorPixel.y - (point.y - map._size.y / 2),
        },
        nextZoom
      );
      map._renderAll();
      map.fire('zoomend');
    }

    container.addEventListener('pointerdown', onPointerDown);
    container.addEventListener('pointermove', onPointerMove);
    container.addEventListener('pointerup', onPointerUp);
    container.addEventListener('pointercancel', onPointerUp);
    container.addEventListener('wheel', onWheel, { passive: false });
    container.addEventListener('dblclick', onDoubleClick);
    container.addEventListener('contextmenu', function (event) {
      // Mantiene el comportamiento táctil de Leaflet: el menú contextual no abre el mapa.
      if (event.cancelable) event.preventDefault();
    });
  };

  LeafletMap.prototype.getCenter = function () {
    return { lat: this._center.lat, lng: this._center.lng };
  };

  LeafletMap.prototype.getZoom = function () {
    return this._zoom;
  };

  LeafletMap.prototype.getSize = function () {
    return { x: this._size.x, y: this._size.y };
  };

  LeafletMap.prototype.getContainer = function () {
    return this._container;
  };

  LeafletMap.prototype.latLngToContainerPoint = function (latlng) {
    var target = toLatLng(latlng);
    if (!target) return { x: 0, y: 0 };
    var centerPixel = project(this._center, this._zoom);
    var targetPixel = project(target, this._zoom);
    return {
      x: targetPixel.x - centerPixel.x + this._size.x / 2,
      y: targetPixel.y - centerPixel.y + this._size.y / 2,
    };
  };

  LeafletMap.prototype.containerPointToLatLng = function (point) {
    var pixel = { x: Number(point && point.x) || 0, y: Number(point && point.y) || 0 };
    var centerPixel = project(this._center, this._zoom);
    return unproject(
      {
        x: centerPixel.x + pixel.x - this._size.x / 2,
        y: centerPixel.y + pixel.y - this._size.y / 2,
      },
      this._zoom
    );
  };

  LeafletMap.prototype.layerPointToLatLng = function (point) {
    return this.containerPointToLatLng(point);
  };

  LeafletMap.prototype.setView = function (center, zoom, options) {
    var target = toLatLng(center);
    if (!target) return this;

    this._center = target;
    if (zoom != null) {
      this._zoom = Math.max(this._minZoom, Math.min(this._maxZoom, clampZoom(zoom)));
    }
    this._renderAll();

    if (!options || options.silent !== true) {
      this.fire('moveend');
    }
    return this;
  };

  LeafletMap.prototype.setZoom = function (zoom) {
    var next = Math.max(this._minZoom, Math.min(this._maxZoom, clampZoom(zoom)));
    if (next === this._zoom) return this;
    this._zoom = next;
    this._renderAll();
    this.fire('zoomend');
    return this;
  };

  LeafletMap.prototype.zoomIn = function (delta) {
    return this.setZoom(this._zoom + (delta == null ? 1 : delta));
  };

  LeafletMap.prototype.zoomOut = function (delta) {
    return this.setZoom(this._zoom - (delta == null ? 1 : delta));
  };

  LeafletMap.prototype.panTo = function (center) {
    var target = toLatLng(center);
    if (!target) return this;
    this._center = target;
    this._renderAll();
    this.fire('moveend');
    return this;
  };

  LeafletMap.prototype.panBy = function (point) {
    var centerPixel = project(this._center, this._zoom);
    this._center = unproject(
      {
        x: centerPixel.x + (Number(point && point.x) || 0),
        y: centerPixel.y + (Number(point && point.y) || 0),
      },
      this._zoom
    );
    this._renderAll();
    this.fire('moveend');
    return this;
  };

  LeafletMap.prototype.invalidateSize = function () {
    this._measure();
    this._renderAll();
    return this;
  };

  LeafletMap.prototype.fitBounds = function (bounds) {
    var corners = null;
    if (Array.isArray(bounds) && bounds.length >= 2) {
      corners = [toLatLng(bounds[0]), toLatLng(bounds[1])];
    } else if (bounds && typeof bounds.getSouthWest === 'function') {
      corners = [bounds.getSouthWest(), bounds.getNorthEast()];
    }
    if (!corners || !corners[0] || !corners[1]) return this;

    var firstBounds = { south: Math.min(corners[0].lat, corners[1].lat), north: Math.max(corners[0].lat, corners[1].lat) };
    var secondBounds = { west: Math.min(corners[0].lng, corners[1].lng), east: Math.max(corners[0].lng, corners[1].lng) };
    this._center = {
      lat: (firstBounds.south + firstBounds.north) / 2,
      lng: (secondBounds.west + secondBounds.east) / 2,
    };

    // Ajuste conservador: parte del zoom máximo y baja hasta que quepa el recuadro.
    var zoom = this._maxZoom;
    while (zoom > this._minZoom) {
      this._zoom = zoom;
      var southWest = this.latLngToContainerPoint({ lat: firstBounds.south, lng: secondBounds.west });
      var northEast = this.latLngToContainerPoint({ lat: firstBounds.north, lng: secondBounds.east });
      if (northEast.x - southWest.x <= this._size.x * 0.9 &&
          southWest.y - northEast.y <= this._size.y * 0.9) {
        break;
      }
      zoom -= 1;
    }
    this._zoom = zoom;

    this._renderAll();
    this.fire('moveend');
    return this;
  };

  LeafletMap.prototype.addLayer = function (layer) {
    if (!layer || this.hasLayer(layer)) return this;
    this._layers.push(layer);
    layer._map = this;
    if (typeof layer.onAdd === 'function') layer.onAdd(this);
    this.fire('layeradd', { layer: layer });
    return this;
  };

  LeafletMap.prototype.removeLayer = function (layer) {
    var index = this._layers.indexOf(layer);
    if (index === -1) return this;
    this._layers.splice(index, 1);
    if (typeof layer.onRemove === 'function') layer.onRemove(this);
    layer._map = null;
    if (this._popupSource === layer) this.closePopup();
    this.fire('layerremove', { layer: layer });
    return this;
  };

  LeafletMap.prototype.hasLayer = function (layer) {
    return this._layers.indexOf(layer) !== -1;
  };

  LeafletMap.prototype.eachLayer = function (callback, context) {
    var snapshot = this._layers.slice();
    for (var i = 0; i < snapshot.length; i += 1) {
      callback.call(context || this, snapshot[i]);
    }
    return this;
  };

  LeafletMap.prototype._renderAll = function () {
    for (var i = 0; i < this._layers.length; i += 1) {
      var layer = this._layers[i];
      if (typeof layer._render === 'function') layer._render(this);
    }
    this._positionPopup();
  };

  LeafletMap.prototype._controlCorner = function (position) {
    var valid = {
      topleft: true,
      topright: true,
      bottomleft: true,
      bottomright: true,
    };
    var key = valid[position] ? position : 'topleft';
    if (this._controlCorners[key]) return this._controlCorners[key];

    var vertical = key.indexOf('top') === 0 ? 'top' : 'bottom';
    var horizontal = key.indexOf('left') !== -1 ? 'left' : 'right';
    var corner = global.document.createElement('div');
    corner.className = 'leaflet-' + vertical + ' leaflet-' + horizontal;
    this._container.appendChild(corner);
    this._controlCorners[key] = corner;
    return corner;
  };

  LeafletMap.prototype._openPopup = function (source) {
    if (!source || source._popupContent == null) return;
    this.closePopup();

    var popup = global.document.createElement('div');
    popup.className = 'leaflet-popup';
    setStyles(popup, {
      position: 'absolute',
      transform: 'translate(-50%, -100%)',
      marginBottom: '10px',
    });

    var wrapper = global.document.createElement('div');
    wrapper.className = 'leaflet-popup-content-wrapper';

    var content = global.document.createElement('div');
    content.className = 'leaflet-popup-content';
    if (typeof source._popupContent === 'string') {
      content.innerHTML = source._popupContent;
    } else if (source._popupContent instanceof global.Node) {
      content.appendChild(source._popupContent);
    } else {
      content.textContent = String(source._popupContent);
    }

    var closeButton = global.document.createElement('a');
    closeButton.className = 'leaflet-popup-close-button';
    closeButton.href = '#';
    closeButton.innerHTML = '&times;';
    closeButton.setAttribute('role', 'button');
    closeButton.setAttribute('aria-label', 'Cerrar');
    closeButton.addEventListener('click', function (event) {
      event.preventDefault();
      event.stopPropagation();
      this.closePopup();
    }.bind(this));

    wrapper.appendChild(content);
    wrapper.appendChild(closeButton);

    var tipContainer = global.document.createElement('div');
    tipContainer.className = 'leaflet-popup-tip-container';
    setStyles(tipContainer, { bottom: '-9px' });
    var tip = global.document.createElement('div');
    tip.className = 'leaflet-popup-tip';
    tipContainer.appendChild(tip);

    popup.appendChild(wrapper);
    popup.appendChild(tipContainer);
    this._panes.popupPane.appendChild(popup);

    this._popup = popup;
    this._popupSource = source;
    this._positionPopup();
    source.fire('popupopen', { popup: popup });
  };

  LeafletMap.prototype._positionPopup = function () {
    if (!this._popup || !this._popupSource) return;
    var source = this._popupSource;
    var latlng = source.getLatLng ? source.getLatLng() : source._latlng;
    if (!latlng) return;
    var point = this.latLngToContainerPoint(latlng);
    this._popup.style.left = point.x + 'px';
    this._popup.style.top = point.y + 'px';
  };

  LeafletMap.prototype.closePopup = function () {
    if (this._popup && this._popup.parentNode) {
      this._popup.parentNode.removeChild(this._popup);
    }
    if (this._popupSource) this._popupSource.fire('popupclose');
    this._popup = null;
    this._popupSource = null;
    return this;
  };

  LeafletMap.prototype.remove = function () {
    var layers = this._layers.slice();
    for (var i = 0; i < layers.length; i += 1) this.removeLayer(layers[i]);
    this.closePopup();
    if (this._container) {
      while (this._container.firstChild) this._container.removeChild(this._container.firstChild);
      this._container.classList.remove('leaflet-container');
    }
    return this;
  };

  // ---------------------------------------------------------------------------
  // Capa de retícula vectorial (equivalente offline de L.tileLayer)
  // ---------------------------------------------------------------------------

  function TileLayer(url, options) {
    Layer.call(this);
    // La URL se conserva por compatibilidad, pero nunca se solicita a la red.
    this._url = url || '';
    this.options = options || {};
    this._canvas = null;
  }

  TileLayer.prototype = Object.create(Layer.prototype);
  TileLayer.prototype.constructor = TileLayer;

  TileLayer.prototype.onAdd = function (map) {
    this._canvas = global.document.createElement('canvas');
    this._canvas.className = 'leaflet-offline-canvas leaflet-tile';
    this._canvas.setAttribute('aria-hidden', 'true');
    map._panes.tilePane.appendChild(this._canvas);
    this._render(map);
  };

  TileLayer.prototype.onRemove = function () {
    if (this._canvas && this._canvas.parentNode) {
      this._canvas.parentNode.removeChild(this._canvas);
    }
    this._canvas = null;
  };

  TileLayer.prototype._render = function (map) {
    var canvas = this._canvas;
    if (!canvas) return;

    var width = Math.max(1, map._size.x);
    var height = Math.max(1, map._size.y);
    var density = Math.max(1, global.devicePixelRatio || 1);
    var pixelWidth = Math.round(width * density);
    var pixelHeight = Math.round(height * density);

    if (canvas.width !== pixelWidth || canvas.height !== pixelHeight) {
      canvas.width = pixelWidth;
      canvas.height = pixelHeight;
    }
    canvas.style.width = width + 'px';
    canvas.style.height = height + 'px';

    var context = canvas.getContext('2d');
    if (!context) return;

    context.setTransform(density, 0, 0, density, 0, 0);

    var dark = !!(global.document.body && global.document.body.classList.contains('dark-mode'));
    context.fillStyle = dark ? '#141313' : '#f9f9f9';
    context.fillRect(0, 0, width, height);

    // Paso de la retícula en metros, redondeado a 1/2/5 x 10^n para que no se vea como ruido.
    var stepMeters = niceStep(Math.max(10, metersPerPixel(map._center.lat, map._zoom) * 72));
    var latitudeStep = stepMeters / METERS_PER_DEGREE;
    var cosLatitude = Math.max(0.01, Math.cos((map._center.lat * Math.PI) / 180));
    var longitudeStep = stepMeters / (METERS_PER_DEGREE * cosLatitude);

    var northWest = map.containerPointToLatLng({ x: 0, y: 0 });
    var southEast = map.containerPointToLatLng({ x: width, y: height });

    var firstLongitudeIndex = Math.floor(southEast.lng / longitudeStep) - 1;
    var lastLongitudeIndex = Math.ceil(northWest.lng / longitudeStep) + 1;
    var firstLatitudeIndex = Math.floor(southEast.lat / latitudeStep) - 1;
    var lastLatitudeIndex = Math.ceil(northWest.lat / latitudeStep) + 1;

    context.lineWidth = 1;
    for (var lngIndex = firstLongitudeIndex; lngIndex <= lastLongitudeIndex; lngIndex += 1) {
      var longitude = lngIndex * longitudeStep;
      var x = map.latLngToContainerPoint({ lat: map._center.lat, lng: longitude }).x;
      if (x < -1 || x > width + 1) continue;
      var majorLongitude = lngIndex % 5 === 0;
      context.strokeStyle = dark
        ? (majorLongitude ? '#2b2929' : '#201f1f')
        : (majorLongitude ? '#e2e4e4' : '#ededed');
      context.beginPath();
      context.moveTo(x + 0.5, 0);
      context.lineTo(x + 0.5, height);
      context.stroke();
    }

    for (var latIndex = firstLatitudeIndex; latIndex <= lastLatitudeIndex; latIndex += 1) {
      var latitude = latIndex * latitudeStep;
      var y = map.latLngToContainerPoint({ lat: latitude, lng: map._center.lng }).y;
      if (y < -1 || y > height + 1) continue;
      var majorLatitude = latIndex % 5 === 0;
      context.strokeStyle = dark
        ? (majorLatitude ? '#2b2929' : '#201f1f')
        : (majorLatitude ? '#e2e4e4' : '#ededed');
      context.beginPath();
      context.moveTo(0, y + 0.5);
      context.lineTo(width, y + 0.5);
      context.stroke();
    }

    // Cruz de referencia discreta en el centro del contenedor.
    context.strokeStyle = dark ? '#3a3838' : '#d8dada';
    context.beginPath();
    context.moveTo(width / 2 - 6, height / 2);
    context.lineTo(width / 2 + 6, height / 2);
    context.moveTo(width / 2, height / 2 - 6);
    context.lineTo(width / 2, height / 2 + 6);
    context.stroke();
  };

  // ---------------------------------------------------------------------------
  // Polilíneas y círculos (SVG)
  // ---------------------------------------------------------------------------

  function createPath(map, options) {
    var path = global.document.createElementNS(SVG_NS, 'path');
    path.setAttribute('fill', 'none');
    path.setAttribute('stroke', options.color || '#0b6c4b');
    path.setAttribute('stroke-width', String(options.weight != null ? options.weight : 3));
    path.setAttribute('stroke-opacity', String(options.opacity != null ? options.opacity : 1));
    path.setAttribute('stroke-linecap', 'round');
    path.setAttribute('stroke-linejoin', 'round');
    map._svg.appendChild(path);
    return path;
  }

  function bindVectorPopup(map, layer, element) {
    element.style.pointerEvents = 'stroke';
    element.addEventListener('click', function (event) {
      event.stopPropagation();
      if (layer._popupContent != null) map._openPopup(layer);
      layer.fire('click');
    });
  }

  function Polyline(latlngs, options) {
    Layer.call(this);
    this._latlngs = normalizeLatLngs(latlngs);
    this.options = options || {};
    this._path = null;
  }

  Polyline.prototype = Object.create(Layer.prototype);
  Polyline.prototype.constructor = Polyline;

  Polyline.prototype.onAdd = function (map) {
    this._path = createPath(map, this.options);
    bindVectorPopup(map, this, this._path);
    this._render(map);
  };

  Polyline.prototype.onRemove = function () {
    if (this._path && this._path.parentNode) this._path.parentNode.removeChild(this._path);
    this._path = null;
  };

  Polyline.prototype._render = function (map) {
    if (!this._path || !this._latlngs.length) return;
    var parts = [];
    for (var i = 0; i < this._latlngs.length; i += 1) {
      var point = map.latLngToContainerPoint(this._latlngs[i]);
      parts.push((i === 0 ? 'M' : 'L') + point.x.toFixed(1) + ' ' + point.y.toFixed(1));
    }
    this._path.setAttribute('d', parts.join(' '));
  };

  Polyline.prototype.setLatLngs = function (latlngs) {
    this._latlngs = normalizeLatLngs(latlngs);
    if (this._map) this._render(this._map);
    return this;
  };

  Polyline.prototype.getLatLngs = function () {
    return this._latlngs.slice();
  };

  Polyline.prototype.getLatLng = function () {
    return this._latlngs.length ? this._latlngs[0] : null;
  };

  function Circle(latlng, options) {
    Layer.call(this);
    this._latlng = toLatLng(latlng) || { lat: 0, lng: 0 };
    this.options = options || {};
    this._element = null;
  }

  Circle.prototype = Object.create(Layer.prototype);
  Circle.prototype.constructor = Circle;

  Circle.prototype.onAdd = function (map) {
    this._element = global.document.createElementNS(SVG_NS, 'circle');
    this._element.setAttribute('stroke', this.options.color || '#0b6c4b');
    this._element.setAttribute('stroke-width', String(this.options.weight != null ? this.options.weight : 2));
    this._element.setAttribute('fill', this.options.fillColor || this.options.color || '#0b6c4b');
    this._element.setAttribute('fill-opacity', String(this.options.fillOpacity != null ? this.options.fillOpacity : 0.2));
    map._svg.appendChild(this._element);
    bindVectorPopup(map, this, this._element);
    this._render(map);
  };

  Circle.prototype.onRemove = function () {
    if (this._element && this._element.parentNode) {
      this._element.parentNode.removeChild(this._element);
    }
    this._element = null;
  };

  Circle.prototype._render = function (map) {
    if (!this._element) return;
    var point = map.latLngToContainerPoint(this._latlng);
    var radiusMeters = Number(this.options.radius);
    if (!isFinite(radiusMeters) || radiusMeters < 0) radiusMeters = 0;
    var radiusPixels = radiusMeters / metersPerPixel(this._latlng.lat, map._zoom);
    this._element.setAttribute('cx', point.x.toFixed(1));
    this._element.setAttribute('cy', point.y.toFixed(1));
    this._element.setAttribute('r', Math.max(2, radiusPixels).toFixed(1));
  };

  Circle.prototype.setLatLng = function (latlng) {
    var target = toLatLng(latlng);
    if (!target) return this;
    this._latlng = target;
    if (this._map) this._render(this._map);
    return this;
  };

  Circle.prototype.setRadius = function (radius) {
    this.options.radius = radius;
    if (this._map) this._render(this._map);
    return this;
  };

  Circle.prototype.getLatLng = function () {
    return { lat: this._latlng.lat, lng: this._latlng.lng };
  };

  Circle.prototype.getRadius = function () {
    return Number(this.options.radius) || 0;
  };

  // ---------------------------------------------------------------------------
  // Marcadores e iconos
  // ---------------------------------------------------------------------------

  function DivIcon(options) {
    this.options = options || {};
  }

  function Marker(latlng, options) {
    Layer.call(this);
    this._latlng = toLatLng(latlng) || { lat: 0, lng: 0 };
    this.options = options || {};
    this._element = null;
    this._elementMap = null;
    this._lastIconSignature = null;
  }

  Marker.prototype = Object.create(Layer.prototype);
  Marker.prototype.constructor = Marker;

  Marker.prototype.onAdd = function (map) {
    var marker = this;
    this._element = global.document.createElement('div');
    this._element.className = 'leaflet-marker-icon';
    this._element.setAttribute('role', 'button');
    setStyles(this._element, {
      position: 'absolute',
      cursor: 'pointer',
      pointerEvents: 'auto',
    });

    this._element.addEventListener('click', function (event) {
      event.stopPropagation();
      if (marker._popupContent != null) map._openPopup(marker);
      marker.fire('click');
    });

    map._panes.markerPane.appendChild(this._element);
    this._applyIcon();
    this._render(map);
  };

  Marker.prototype.onRemove = function () {
    if (this._element && this._element.parentNode) {
      this._element.parentNode.removeChild(this._element);
    }
    this._element = null;
    this._elementMap = null;
    this._lastIconSignature = null;
  };

  Marker.prototype._applyIcon = function () {
    var element = this._element;
    if (!element) return;

    var icon = this.options.icon || new DivIcon({ html: '', iconSize: [12, 12] });
    var iconOptions = icon.options || {};
    var width = Array.isArray(iconOptions.iconSize) ? Number(iconOptions.iconSize[0]) : 12;
    var height = Array.isArray(iconOptions.iconSize) ? Number(iconOptions.iconSize[1]) : 12;
    var anchor = Array.isArray(iconOptions.iconAnchor) ? iconOptions.iconAnchor : [width / 2, height / 2];
    var html = iconOptions.html == null ? '' : String(iconOptions.html);
    var className = iconOptions.className ? String(iconOptions.className) : '';
    var signature = className + '\u0000' + html + '\u0000' + width + 'x' + height;

    if (signature !== this._lastIconSignature) {
      element.className = 'leaflet-marker-icon' + (className ? ' ' + className : '');
      element.innerHTML = html;
      element.style.width = (isFiniteNumber(width) ? width : 12) + 'px';
      element.style.height = (isFiniteNumber(height) ? height : 12) + 'px';
      element.style.marginLeft = (-Number(anchor[0] || 0)) + 'px';
      element.style.marginTop = (-Number(anchor[1] || 0)) + 'px';
      this._lastIconSignature = signature;
    }

    if (!iconOptions.html) {
      // Icono por defecto: punto sólido si el autor no proporcionó HTML.
      element.style.background = '#0b6c4b';
      element.style.border = '2px solid #ffffff';
      element.style.borderRadius = '50%';
      element.style.boxSizing = 'border-box';
    } else {
      // Al cambiar de icono por defecto a uno HTML se limpian los estilos del punto.
      element.style.background = '';
      element.style.border = '';
      element.style.borderRadius = '';
    }
  };

  Marker.prototype._render = function (map) {
    if (!this._element) return;
    var point = map.latLngToContainerPoint(this._latlng);
    this._element.style.left = point.x + 'px';
    this._element.style.top = point.y + 'px';
    this._elementMap = map;
  };

  Marker.prototype.setLatLng = function (latlng) {
    var target = toLatLng(latlng);
    if (!target) return this;
    this._latlng = target;
    if (this._map) this._render(this._map);
    return this;
  };

  Marker.prototype.getLatLng = function () {
    return { lat: this._latlng.lat, lng: this._latlng.lng };
  };

  Marker.prototype.setIcon = function (icon) {
    this.options.icon = icon;
    this._applyIcon();
    if (this._map) this._render(this._map);
    return this;
  };

  Marker.prototype.setZIndexOffset = function () {
    return this;
  };

  // ---------------------------------------------------------------------------
  // Control de zoom
  // ---------------------------------------------------------------------------

  function ZoomControl(options) {
    this.options = options || {};
    this._map = null;
    this._container = null;
  }

  ZoomControl.prototype.addTo = function (map) {
    if (this._map) this.remove();
    this._map = map;

    var control = global.document.createElement('div');
    control.className = 'leaflet-control-zoom leaflet-bar leaflet-control';

    var zoomIn = global.document.createElement('a');
    zoomIn.className = 'leaflet-control-zoom-in';
    zoomIn.href = '#';
    zoomIn.innerHTML = '+';
    zoomIn.setAttribute('role', 'button');
    zoomIn.setAttribute('aria-label', 'Acercar');
    zoomIn.addEventListener('click', function (event) {
      event.preventDefault();
      event.stopPropagation();
      map.zoomIn();
    });

    var zoomOut = global.document.createElement('a');
    zoomOut.className = 'leaflet-control-zoom-out';
    zoomOut.href = '#';
    zoomOut.innerHTML = '&minus;';
    zoomOut.setAttribute('role', 'button');
    zoomOut.setAttribute('aria-label', 'Alejar');
    zoomOut.addEventListener('click', function (event) {
      event.preventDefault();
      event.stopPropagation();
      map.zoomOut();
    });

    control.appendChild(zoomIn);
    control.appendChild(zoomOut);
    map._controlCorner(this.options.position || 'topleft').appendChild(control);
    this._container = control;
    return this;
  };

  ZoomControl.prototype.remove = function () {
    if (this._container && this._container.parentNode) {
      this._container.parentNode.removeChild(this._container);
    }
    this._container = null;
    this._map = null;
    return this;
  };

  // ---------------------------------------------------------------------------
  // Fachada compatible con Leaflet
  // ---------------------------------------------------------------------------

  var L = {
    version: '1.9.4-cronobus-offline',
    __cronobusOffline: true,

    map: function (container, options) {
      return new LeafletMap(container, options);
    },

    tileLayer: function (url, options) {
      return new TileLayer(url, options);
    },

    polyline: function (latlngs, options) {
      return new Polyline(latlngs, options);
    },

    circle: function (latlng, options) {
      return new Circle(latlng, options);
    },

    marker: function (latlng, options) {
      return new Marker(latlng, options);
    },

    divIcon: function (options) {
      return new DivIcon(options);
    },

    latLng: function (lat, lng) {
      return toLatLng([lat, lng]);
    },

    control: {
      zoom: function (options) {
        return new ZoomControl(options);
      },
    },

    Map: LeafletMap,
    TileLayer: TileLayer,
    Polyline: Polyline,
    Circle: Circle,
    Marker: Marker,
    DivIcon: DivIcon,
    Control: {
      Zoom: ZoomControl,
    },
  };

  global.L = L;

  if (global.console && global.console.info) {
    global.console.info('CronoBus: motor de mapa offline activo (0 peticiones de red).');
  }
})(typeof window !== 'undefined' ? window : this);
