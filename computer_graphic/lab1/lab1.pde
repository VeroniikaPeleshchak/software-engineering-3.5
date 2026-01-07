PVector axisP1, axisP2;
float angle = 0;
boolean autoRotate = true;
int axisMode = 2; // 0=X, 1=Y, 2=Z

PVector[] cube;
PVector[] tetra;

void setup() {
  size(600, 600, P3D);
  
  cube = new PVector[]{
    new PVector(-50, -50, -50),
    new PVector( 50, -50, -50),
    new PVector( 50,  50, -50),
    new PVector(-50,  50, -50),
    new PVector(-50, -50,  50),
    new PVector( 50, -50,  50),
    new PVector( 50,  50,  50),
    new PVector(-50,  50,  50)
  };
  
  tetra = new PVector[]{
    new PVector(0, 0, 100),
    new PVector(100, 0, -100),
    new PVector(-100, 0, -100),
    new PVector(0, 150, -50)
  };
  
  setAxis();
}

void draw() {
  background(240);
  lights();
  translate(width/2, height/2, 0);
  rotateX(-PI/6);
  rotateY(PI/6);
  
  stroke(200, 0, 0);
  strokeWeight(2);
  line(axisP1.x, axisP1.y, axisP1.z, axisP2.x, axisP2.y, axisP2.z);
  
  if (autoRotate) angle += 0.02;
  
  stroke(0);
  drawEdges(rotateShape(cube, angle));
  
  stroke(0,0,200);
  drawEdges(rotateShape(tetra, angle));
}

PVector[] rotateShape(PVector[] pts, float theta) {
  PVector[] out = new PVector[pts.length];
  for (int i = 0; i < pts.length; i++) {
    out[i] = rodrigues(pts[i], axisP1, axisP2, theta);
  }
  return out;
}

PVector rodrigues(PVector v, PVector p1, PVector p2, float theta) {
  PVector u = PVector.sub(p2, p1);
  u.normalize();
  PVector w = PVector.sub(v, p1);
  
  PVector term1 = PVector.mult(w, cos(theta));
  PVector term2 = PVector.mult(u.cross(w), sin(theta));
  PVector term3 = PVector.mult(u, u.dot(w) * (1 - cos(theta)));
  
  PVector res = PVector.add(PVector.add(term1, term2), term3);
  return PVector.add(res, p1);
}

void drawEdges(PVector[] v) {
  int[][] cubeEdges = {
    {0,1},{1,2},{2,3},{3,0},
    {4,5},{5,6},{6,7},{7,4},
    {0,4},{1,5},{2,6},{3,7}
  };
  
  int[][] tetraEdges = {
    {0,1},{0,2},{0,3},{1,2},{1,3},{2,3}
  };
  
  if (v.length == 8) {
    for (int[] e : cubeEdges) {
      line(v[e[0]].x, v[e[0]].y, v[e[0]].z,
           v[e[1]].x, v[e[1]].y, v[e[1]].z);
    }
  } else if (v.length == 4) {
    for (int[] e : tetraEdges) {
      line(v[e[0]].x, v[e[0]].y, v[e[0]].z,
           v[e[1]].x, v[e[1]].y, v[e[1]].z);
    }
  }
}

void keyPressed() {
  if (keyCode == LEFT) angle -= 0.1;
  if (keyCode == RIGHT) angle += 0.1;
  if (keyCode == UP) { axisMode = (axisMode + 1) % 3; setAxis(); }
  if (keyCode == DOWN) { axisMode = (axisMode + 2) % 3; setAxis(); }
  if (key == ' ') autoRotate = !autoRotate;
}

void setAxis() {
  if (axisMode == 0) { axisP1 = new PVector(-100, 0, 0); axisP2 = new PVector(100, 0, 0); }
  if (axisMode == 1) { axisP1 = new PVector(0, -100, 0); axisP2 = new PVector(0, 100, 0); }
  if (axisMode == 2) { axisP1 = new PVector(0, 0, -100); axisP2 = new PVector(0, 0, 100); }
}
