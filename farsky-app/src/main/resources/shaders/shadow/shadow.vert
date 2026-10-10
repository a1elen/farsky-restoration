#version 110

uniform float time;

// Wave variables (same names/semantics as world.vert so swaying geometry
// casts matching shadows)
uniform bool wave, useAlphaAsHeight;
uniform float height, factor, offset, amplitude;

void main(void){
	vec4 vertex = gl_Vertex;

	// Water waves on objects. Rotation => color.a = rot / 1000.0 in radians
	if (wave){
		float H;
		if (useAlphaAsHeight) H = gl_Color.a / height;
		else H = vertex.y / height;

		float waveFact = cos(time/2000.0*factor - H * 3.1415 * 2.0 + offset);
		vertex.x = vertex.x + H * H * waveFact * amplitude * cos(gl_Color.a * 1000.0);
		vertex.z = vertex.z + H * H * waveFact * amplitude * sin(gl_Color.a * 1000.0);
	}

	gl_Position = gl_ModelViewProjectionMatrix * vertex;
	gl_TexCoord[0] = gl_MultiTexCoord0;
}
