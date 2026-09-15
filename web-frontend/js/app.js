const viewport = document.getElementById('viewport');
const container = document.getElementById('tile-container');
const zoomLabel = document.getElementById('zoom-level');

const manager = new ViewportManager(256);
const purger = new TilePurger(container);

const imageId = 'imagen1';

let isDragging = false;
let startX = 0;
let startY = 0;

/*

* Actualiza la vista de acuerdo con el estado actual
* del zoom y desplazamiento.
  */
  function updateView() {

  zoomLabel.textContent = manager.zoom;

  const visibleTiles = manager.getVisibleTiles(
  viewport.clientWidth,
  viewport.clientHeight
  );

  const requiredTiles = visibleTiles.map(tile => ({

  
   z: tile.z,
   x: tile.x,
   y: tile.y,

   url: `/tiles/${imageId}/${tile.z}/${tile.x}_${tile.y}.jpg`,

   left: tile.x * manager.tileSize + manager.panX,
   top: tile.y * manager.tileSize + manager.panY
  

  }));

  purger.syncAndPurge(requiredTiles);
  }

/*

* Inicio del desplazamiento con el mouse.
  */
  viewport.addEventListener('mousedown', (event) => {

  isDragging = true;

  startX = event.clientX - manager.panX;
  startY = event.clientY - manager.panY;

});

/*

* Movimiento del mouse mientras se arrastra.
  */
  window.addEventListener('mousemove', (event) => {

  if (!isDragging) {
  return;
  }

  manager.panX = event.clientX - startX;
  manager.panY = event.clientY - startY;

  updateView();

});

/*

* Finaliza el desplazamiento.
  */
  window.addEventListener('mouseup', () => {

  isDragging = false;

});

/*

* Control del zoom mediante la rueda del mouse.
  */
  viewport.addEventListener('wheel', (event) => {

  event.preventDefault();

  if (event.deltaY < 0) {

  
   if (manager.zoom < manager.maxZoom) {
       manager.zoom++;
       updateView();
   }
  

  } else if (event.deltaY > 0) {

  
   if (manager.zoom > 0) {
       manager.zoom--;
       updateView();
   }
  

  }

});

/*

* Actualiza la vista cuando cambia el tamano
* de la ventana del navegador.
  */
  window.addEventListener('resize', () => {

  updateView();

});

/*

* Inicializacion de la aplicacion.
  */
  updateView();
