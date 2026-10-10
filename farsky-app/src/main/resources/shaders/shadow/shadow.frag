#version 110

// Alpha test for chunk elements (grass, kelp, corals...) so their cutout
// textures cast properly shaped shadows instead of full quads.
uniform sampler2D colorTex;

void main(){
	if (texture2D(colorTex, gl_TexCoord[0].st).a < 0.1){
		discard;
	}

	gl_FragColor = vec4(1.0);
}
