#version 110

// One separable pass of the depth aware blur over the half resolution ambient
// occlusion buffer. Every tap is weighted by how close its scene depth is to the
// centre pixel, so the blur removes the sampling grain without smearing the
// occlusion across silhouettes.
uniform sampler2D aoTex;
uniform sampler2D depthTex;
uniform vec2 blurDir;

void main(){
	vec2 uv = gl_TexCoord[0].st;
	float centerDepth = texture2D(depthTex, uv).r;
	float sum = texture2D(aoTex, uv).r;
	float weightSum = 1.0;

	// The depth tolerance has to grow with distance: far away geometry spans a
	// large view space range per depth unit, so a fixed tolerance would stop it
	// from blurring at all.
	float range = 0.0008 + centerDepth * 0.01;

	const int SAMPLES = 4;
	for (int i = 1; i <= SAMPLES; i++){
		float fi = float(i);
		float spatial = exp(-fi * fi / 6.0);
		vec2 a = uv + blurDir * fi;
		vec2 b = uv - blurDir * fi;
		float wa = spatial * exp(-abs(texture2D(depthTex, a).r - centerDepth) / range);
		float wb = spatial * exp(-abs(texture2D(depthTex, b).r - centerDepth) / range);
		sum += texture2D(aoTex, a).r * wa + texture2D(aoTex, b).r * wb;
		weightSum += wa + wb;
	}

	float ao = sum / weightSum;
	gl_FragColor = vec4(ao, ao, ao, 1.0);
}
