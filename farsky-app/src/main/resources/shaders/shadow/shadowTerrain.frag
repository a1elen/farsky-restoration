#version 110

// Terrain is fully opaque, only the depth written by this pass matters.
void main(){
	gl_FragColor = vec4(1.0);
}
