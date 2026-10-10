#version 110

// Screen space ambient occlusion. Reconstructs view space positions from the
// scene depth texture, estimates the surface normal and darkens pixels whose
// neighbourhood is occluded (contact areas between geometry and sea floor).
uniform sampler2D depthTex;

uniform mat4 invProjection;
uniform mat4 projection;
uniform float radius;
uniform float bias;
uniform float fogDistance;
uniform vec2 depthTexel;

vec3 viewPosAt(vec2 uv, float depth){
	vec4 v = invProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
	return v.xyz / v.w;
}

void main(){
	vec2 uv = gl_TexCoord[0].st;
	float depth = texture2D(depthTex, uv).r;
	if (depth >= 0.99999){
		gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
		return;
	}

	vec3 origin = viewPosAt(uv, depth);

	// Stable per-pixel rotation (no per-frame randomness, so the AO does not
	// flicker; the spatial noise is cleaned up by the depth aware blur pass).
	float rotation = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) * 6.2831853;

	// Normal rebuilt from the screen space derivatives of the view position.
	// Finite differences over neighbouring depth taps break down at grazing
	// angles (the taps span huge depth jumps and the normal flips), while the
	// hardware derivatives stay stable and follow the surface exactly.
	vec3 normal = normalize(cross(dFdx(origin), dFdy(origin)));
	if (normal.z < 0.0) normal = -normal;
	if (dot(normal, normal) < 0.0001) normal = vec3(0.0, 0.0, 1.0);

	// Randomized tangent frame around the normal
	vec3 tangent = vec3(-normal.y, normal.x, 0.0);
	if (length(tangent) < 0.001) tangent = vec3(1.0, 0.0, 0.0);
	tangent = normalize(tangent);
	vec3 bitangent = cross(normal, tangent);
	float ca = cos(rotation);
	float sa = sin(rotation);
	vec3 T = tangent * ca + bitangent * sa;
	vec3 B = -tangent * sa + bitangent * ca;

	const int SAMPLES = 28;
	float occlusion = 0.0;
	for (int i = 0; i < SAMPLES; i++){
		float fi = float(i) + 0.5;
		float ang = fi * 2.39996323;
		float ring = sqrt(fi / float(SAMPLES));
		vec3 s = vec3(cos(ang) * ring, sin(ang) * ring, sqrt(max(0.0, 1.0 - ring * ring)));
		vec3 samplePos = origin + (T * s.x + B * s.y + normal * s.z) * radius;

		vec4 clip = projection * vec4(samplePos, 1.0);
		if (clip.w <= 0.0) continue;
		vec2 suv = clip.xy / clip.w * 0.5 + 0.5;
		if (suv.x < 0.0 || suv.x > 1.0 || suv.y < 0.0 || suv.y > 1.0) continue;

		vec3 scenePos = viewPosAt(suv, texture2D(depthTex, suv).r);
		float diff = scenePos.z - samplePos.z;
		occlusion += step(bias, diff) * (1.0 - step(radius, diff));
	}

	float ao = clamp(1.0 - occlusion / float(SAMPLES), 0.0, 1.0);

	// AO is only meaningful close to the camera, fade it out with distance
	float viewDistance = length(origin);
	float fade = 1.0 - clamp(viewDistance / min(fogDistance, 300.0), 0.0, 1.0);
	// Surfaces nearly edge on to the camera have unreliable normals, which shows
	// up as phantom AO that pops in and out as the camera rotates. Fade it there;
	// the ramp is deliberately wide so the noisy normal does not cut off hard
	// along silhouettes and leave them looking jagged.
	fade *= smoothstep(0.05, 0.55, abs(normal.z));
	ao = mix(1.0, ao, fade);

	gl_FragColor = vec4(ao, ao, ao, 1.0);
}
