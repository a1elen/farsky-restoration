#version 110

// Combines the resolved scene color with the ambient occlusion buffer and
// reprojects the previous frame for motion blur. Runs first, everything else
// works on top of the composited back buffer.
uniform sampler2D sceneTex;
uniform sampler2D aoTex;
uniform sampler2D depthTex;

uniform bool useAO;
uniform float aoStrength;
uniform bool useMotionBlur;
uniform float motionStrength;
uniform float fogDistance;

uniform mat4 invViewProj;
uniform mat4 prevViewProj;

void main(){
	vec2 uv = gl_TexCoord[0].st;
	vec3 color = texture2D(sceneTex, uv).rgb;
	float depth = texture2D(depthTex, uv).r;

	// Motion blur: rebuild the world position of this pixel, project it into
	// the previous frame and smear the scene color along the resulting motion.
	if (useMotionBlur && depth < 0.99999){
		vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
		vec4 world = invViewProj * clip;
		world /= world.w;
		vec4 prevClip = prevViewProj * world;
		if (prevClip.w > 0.0){
			vec2 prevUv = prevClip.xy / prevClip.w * 0.5 + 0.5;
			vec2 velocity = (uv - prevUv) * motionStrength;
			float len = length(velocity);
			if (len > 0.0015){
				if (len > 0.035) velocity *= 0.035 / len;
				vec3 sum = vec3(0.0);
				float weightSum = 0.0;
				for (int i = 0; i < 8; i++){
					float t = float(i) / 7.0;
					float weight = 1.0 - t * 0.5;
					sum += texture2D(sceneTex, uv - velocity * t).rgb * weight;
					weightSum += weight;
				}
				color = sum / weightSum;
			}
		}
	}

	// Ambient occlusion. Already blurred and depth weighted by the SSAO pass, so
	// a single bilinear fetch is enough here.
	if (useAO){
		float ao = clamp(texture2D(aoTex, uv).r, 0.0, 1.0);
		color *= mix(1.0, ao, aoStrength);
	}

	gl_FragColor = vec4(color, 1.0);
}
