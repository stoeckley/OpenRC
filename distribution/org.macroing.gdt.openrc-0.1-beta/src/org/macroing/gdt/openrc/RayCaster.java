/**
 * Copyright 2009 - 2015 J&#246;rgen Lundgren
 * 
 * This file is part of org.macroing.gdt.openrc.
 * 
 * org.macroing.gdt.openrc is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * org.macroing.gdt.openrc is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with org.macroing.gdt.openrc. If not, see <http://www.gnu.org/licenses/>.
 */
package org.macroing.gdt.openrc;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.WritableRaster;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;

import com.amd.aparapi.Kernel;
import com.amd.aparapi.Range;

/**
 * An OpenCL-based Ray Caster using Aparapi.
 * <p>
 * The values in the {@code float} array {@code cameraValues} consists of Eye X, Eye Y, Eye Z, Up X, Up Y, Up Z, Look-at X, Look-at Y, Look-at Z, ONB-U X, ONB-U Y, ONB-U Z, ONB-V X, ONB-V Y, ONB-V Z, ONB-W X, ONB-W Y, ONB-W Z and View-plane distance.
 * <p>
 * If you don't know what ONB stands for, then it is OrthoNormal Basis.
 * <p>
 * The values in the {@code float} array {@code intersections} consists of Shape Offset and Distance (T), for each shape currently being intersected by a ray.
 * <p>
 * The values in the {@code float} array {@code lights} consists of Type, Size and {@code float}[Size], for each light defined.
 * <p>
 * The values in the {@code float} array {@code rays} consists of Origin X, Origin Y, Origin Z, Direction X, Direction Y and Direction Z, for each ray fired from each pixel.
 * <p>
 * The values in the {@code float} array {@code shapes} consists of Type, Size and {@code float}[Size], for each shape defined.
 * <p>
 * The controls currently defined are the following:
 * <ul>
 * <li>A - Move left.</li>
 * <li>D - Move right.</li>
 * <li>E - Display the current execution mode to standard output.</li>
 * <li>ESC - Exit the program. You may have to press a few times if you're using the execution mode JTP (Java Thread Pool), as it's pretty unresponsive.</li>
 * <li>S - Move backward.</li>
 * <li>T - Toggle between the two execution modes GPU and JTP (Java Thread Pool).</li>
 * <li>W - Move forward.</li>
 * </ul>
 * <p>
 * The features currently supported are the following:
 * <ul>
 * <li>Shapes such as planes, spheres and triangles.</li>
 * <li>Lights such as point lights.</li>
 * <li>Textures such as normal color textures.</li>
 * <li>Texture mapping such as spherical texture mapping.</li>
 * <li>Ray Casting, which corresponds to the primary rays of Ray Tracing. This means no reflections and refractions are shown. At least not yet.</li>
 * </ul>
 * <p>
 * Note that the features above will probably be expanded upon with time.
 * 
 * @since 1.0.0
 * @author J&#246;rgen Lundgren
 */
public final class RayCaster extends Kernel implements KeyListener {
//	The following variables are only used by the CPU:
	private final AtomicBoolean isPrintingExecutionMode = new AtomicBoolean();
	private final AtomicBoolean isRunning = new AtomicBoolean();
	private final AtomicBoolean isTerminationRequested = new AtomicBoolean();
	private final AtomicBoolean isTogglingExecutionMode = new AtomicBoolean();
	private final boolean[] isKeyPressed;
	private final BufferedImage bufferedImage;
	private final Camera camera;
	private final FPSCounter fPSCounter;
	private final JFrame jFrame;
	private final Range range;
	private final Scene scene;
	
//	The following variables are used by the GPU (and if not all, at least a few are also used by the CPU):
	private final float[] cameraValues;
	private final float[] intersections;
	private final float[] lights;
	private final float[] pixels;
	private final float[] rays;
	private final float[] shapes;
	private final int height;
	private final int lightsLength;
	private final int shapeIndicesLength;
	private final int textureHeight;
	private final int textureWidth;
	private final int width;
	private final int[] rGB;
	private final int[] shapeIndices;
	private final int[] texture;
	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	
	private RayCaster() {
		final Scene scene = Scene.create();
		
		final Texture texture = Texture.create("Texture.jpg");
		
		this.bufferedImage = new BufferedImage(Constants.WIDTH, Constants.HEIGHT, BufferedImage.TYPE_INT_RGB);
		this.camera = new Camera();
		this.cameraValues = this.camera.getArray();
		this.fPSCounter = new FPSCounter();
		this.height = this.bufferedImage.getHeight();
		this.intersections = doCreateIntersections(this.bufferedImage.getWidth() * this.bufferedImage.getHeight());
		this.isKeyPressed = new boolean[256];
		this.jFrame = doCreateJFrame(this.bufferedImage, this.camera, this.fPSCounter);
		this.lights = scene.toLightArray();
		this.lightsLength = this.lights.length;
		this.pixels = doCreatePixels(this.bufferedImage.getWidth() * this.bufferedImage.getHeight());
		this.range = Range.create(this.bufferedImage.getWidth() * this.bufferedImage.getHeight());
		this.rays = doCreateRays(this.bufferedImage.getWidth() * this.bufferedImage.getHeight());
		this.rGB = doToRGB(this.bufferedImage);
		this.scene = scene;
		this.shapeIndices = scene.createShapeIndices();
		this.shapeIndicesLength = this.shapeIndices.length;
		this.shapes = scene.toShapeArray();
		this.texture = texture.getData();
		this.textureHeight = texture.getHeight();
		this.textureWidth = texture.getWidth();
		this.width = this.bufferedImage.getWidth();
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Overridden to handle key typing.
	 * 
	 * @param e a {@code KeyEvent}
	 */
	@Override
	public void keyTyped(final KeyEvent e) {
//		Do nothing here.
	}
	
	/**
	 * Overridden to handle key pressing.
	 * 
	 * @param e a {@code KeyEvent}
	 */
	@Override
	public void keyPressed(final KeyEvent e) {
		this.isKeyPressed[e.getKeyCode()] = true;
	}
	
	/**
	 * Overridden to handle key releasing.
	 * 
	 * @param e a {@code KeyEvent}
	 */
	@Override
	public void keyReleased(final KeyEvent e) {
		this.isKeyPressed[e.getKeyCode()] = false;
	}
	
	/**
	 * This is what the {@code Kernel} executes on the GPU (or in the CPU).
	 */
	@Override
	public void run() {
//		Initialize index and offset values:
		final int index = getGlobalId();
		final int pixelOffset = index * Constants.SIZE_OF_PIXEL_IN_PIXELS;
		final int rayOffset = index * Constants.SIZE_OF_RAY_IN_RAYS;
		
//		Initialize the U- and V-coordinates:
		final float u = index % this.width - this.width / 2.0F + 0.5F;
		final float v = index / this.width - this.height / 2.0F + 0.5F;
		
//		Update the origin point and direction vector of the ray to fire:
		this.rays[rayOffset + Constants.RELATIVE_OFFSET_OF_RAY_ORIGIN_POINT_IN_RAYS + 0] = this.cameraValues[Camera.ABSOLUTE_OFFSET_OF_CAMERA_EYE_POINT + 0];
		this.rays[rayOffset + Constants.RELATIVE_OFFSET_OF_RAY_ORIGIN_POINT_IN_RAYS + 1] = this.cameraValues[Camera.ABSOLUTE_OFFSET_OF_CAMERA_EYE_POINT + 1];
		this.rays[rayOffset + Constants.RELATIVE_OFFSET_OF_RAY_ORIGIN_POINT_IN_RAYS + 2] = this.cameraValues[Camera.ABSOLUTE_OFFSET_OF_CAMERA_EYE_POINT + 2];
		this.rays[rayOffset + Constants.RELATIVE_OFFSET_OF_RAY_DIRECTION_VECTOR_IN_RAYS + 0] = this.cameraValues[Camera.ABSOLUTE_OFFSET_OF_CAMERA_ORTHONORMAL_BASIS_U_VECTOR + 0] * u + this.cameraValues[Camera.ABSOLUTE_OFFSET_OF_CAMERA_ORTHONORMAL_BASIS_V_VECTOR + 0] * v - this.cameraValues[Camera.ABSOLUTE_OFFSET_OF_CAMERA_ORTHONORMAL_BASIS_W_VECTOR + 0] * this.cameraValues[Camera.ABSOLUTE_OFFSET_OF_CAMERA_VIEW_PLANE_DISTANCE_SCALAR];
		this.rays[rayOffset + Constants.RELATIVE_OFFSET_OF_RAY_DIRECTION_VECTOR_IN_RAYS + 1] = this.cameraValues[Camera.ABSOLUTE_OFFSET_OF_CAMERA_ORTHONORMAL_BASIS_U_VECTOR + 1] * u + this.cameraValues[Camera.ABSOLUTE_OFFSET_OF_CAMERA_ORTHONORMAL_BASIS_V_VECTOR + 1] * v - this.cameraValues[Camera.ABSOLUTE_OFFSET_OF_CAMERA_ORTHONORMAL_BASIS_W_VECTOR + 1] * this.cameraValues[Camera.ABSOLUTE_OFFSET_OF_CAMERA_VIEW_PLANE_DISTANCE_SCALAR];
		this.rays[rayOffset + Constants.RELATIVE_OFFSET_OF_RAY_DIRECTION_VECTOR_IN_RAYS + 2] = this.cameraValues[Camera.ABSOLUTE_OFFSET_OF_CAMERA_ORTHONORMAL_BASIS_U_VECTOR + 2] * u + this.cameraValues[Camera.ABSOLUTE_OFFSET_OF_CAMERA_ORTHONORMAL_BASIS_V_VECTOR + 2] * v - this.cameraValues[Camera.ABSOLUTE_OFFSET_OF_CAMERA_ORTHONORMAL_BASIS_W_VECTOR + 2] * this.cameraValues[Camera.ABSOLUTE_OFFSET_OF_CAMERA_VIEW_PLANE_DISTANCE_SCALAR];
		
//		Normalize the ray direction vector:
		doNormalize(this.rays, rayOffset + Constants.RELATIVE_OFFSET_OF_RAY_DIRECTION_VECTOR_IN_RAYS);
		
//		Calculate the distance to the closest shape, if any:
		final float distance = doGetIntersection();
		
//		Initialize the RGB-values of the current pixel to black:
		int r = 0;
		int g = 0;
		int b = 0;
		
		if(distance > 0.0F && distance < Constants.MAXIMUM_DISTANCE) {
//			Initialize needed offset values:
			final int intersectionOffset = index * Constants.SIZE_OF_INTERSECTION_IN_INTERSECTIONS;
			final int shapeOffset = (int)(this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SHAPE_OFFSET_SCALAR_IN_INTERSECTIONS]);
			
//			Calculate the shading for the intersected shape at the surface intersection point:
			final float shading = doCalculateShading(intersectionOffset, shapeOffset);
			
			if(this.shapes[shapeOffset + Shape.RELATIVE_OFFSET_OF_SHAPE_TYPE_SCALAR_IN_SHAPES] == Plane.TYPE_PLANE) {
//				A temporary way to give the plane some color:
				this.pixels[pixelOffset + 0] = 255.0F;
				this.pixels[pixelOffset + 1] = 255.0F;
				this.pixels[pixelOffset + 2] = 255.0F;
			}
			
			if(this.shapes[shapeOffset + Shape.RELATIVE_OFFSET_OF_SHAPE_TYPE_SCALAR_IN_SHAPES] == Sphere.TYPE_SPHERE) {
//				Perform spherical texture mapping on the sphere:
				doPerformSphericalTextureMapping(intersectionOffset, pixelOffset, shapeOffset);
			}
			
			if(this.shapes[shapeOffset + Shape.RELATIVE_OFFSET_OF_SHAPE_TYPE_SCALAR_IN_SHAPES] == Triangle.TYPE_TRIANGLE) {
//				A temporary way to give the triangle some color:
				this.pixels[pixelOffset + 0] = 255.0F;
				this.pixels[pixelOffset + 1] = 255.0F;
				this.pixels[pixelOffset + 2] = 255.0F;
			}
			
//			Update the RGB-values of the current pixel, given the RGB-values of the intersected shape:
			r = (int)(this.pixels[pixelOffset + 0] * shading);
			g = (int)(this.pixels[pixelOffset + 1] * shading);
			b = (int)(this.pixels[pixelOffset + 2] * shading);
		}
		
//		Set the RGB-value of the current pixel:
		this.rGB[index] = doToRGB(r, g, b);
	}
	
	/**
	 * Called to start the Ray Caster.
	 */
	public void start() {
//		Add a shutdown hook that stops the execution and subsequently disposes of any resources:
		Runtime.getRuntime().addShutdownHook(new Thread(() -> this.isRunning.set(false)));
		
//		Initialize the field isRunning to true:
		this.isRunning.set(true);
		
//		Add a KeyListener to the JFrame:
		SwingUtilities2.doInvokeAndWait(() -> this.jFrame.addKeyListener(this));
		
//		Make this Kernel instance explicit, such that we have to take care of all array transfers to and from the GPU:
		setExplicit(true);
		
//		Tell the API to fetch the below arrays and their values before executing this Kernel instance (they will be transferred to the GPU):
		put(this.intersections);
		put(this.lights);
		put(this.rays);
		put(this.shapes);
		put(this.rGB);
		
		while(this.isRunning.get()) {
//			Update the current frame:
			doUpdate();
			
//			Tell the API to fetch the camera values before executing this Kernel instance (it will be transferred to the GPU every cycle):
			put(this.cameraValues);
			
//			Tell the API to fetch the shape indices before executing this Kernel instance (it will be transferred to the GPU every cycle):
			put(this.shapeIndices);
			
//			Execute this Kernel instance:
			execute(this.range);
			
//			Fetch the RGB-values calculated in the GPU to the rGB array, so we can display the result:
			get(this.rGB);
			
//			Tell the JFrame to repaint itself:
			this.jFrame.repaint();
			
//			Update the FPS in the FPSCounter:
			this.fPSCounter.update();
		}
		
//		Tell the Kernel to dispose of any resources used.
		dispose();
		
//		Tell the JFrame to dispose of any resources used.
		this.jFrame.dispose();
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * The entry-point of this Ray Caster.
	 * 
	 * @param args these are not used
	 */
	public static void main(final String[] args) {
		final
		RayCaster rayCaster = SwingUtilities2.doRunInEDT(() -> new RayCaster());
		rayCaster.start();
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	
	@SuppressWarnings("unused")
	private float doCalculateShading(final int intersectionOffset, final int shapeOffset) {
//		Initialize the shading value:
		float shading = 0.0F;
		
//		Initialize values from the intersection:
		final float surfaceIntersectionX = this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_INTERSECTION_POINT_IN_INTERSECTIONS + 0];
		final float surfaceIntersectionY = this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_INTERSECTION_POINT_IN_INTERSECTIONS + 1];
		final float surfaceIntersectionZ = this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_INTERSECTION_POINT_IN_INTERSECTIONS + 2];
		final float surfaceNormalX = this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_NORMAL_VECTOR_IN_INTERSECTIONS + 0];
		final float surfaceNormalY = this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_NORMAL_VECTOR_IN_INTERSECTIONS + 1];
		final float surfaceNormalZ = this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_NORMAL_VECTOR_IN_INTERSECTIONS + 2];
		
		for(int i = 0, j = 0; i < this.lightsLength; i += j) {
//			Initialize the temporary type and size variables of the current light:
			final float lightType = this.lights[i + Light.RELATIVE_OFFSET_OF_LIGHT_TYPE_SCALAR_IN_LIGHTS];
			final float lightSize = this.lights[i + Light.RELATIVE_OFFSET_OF_LIGHT_SIZE_SCALAR_IN_LIGHTS];
			
//			Set the light size as increment for the next loop iteration:
			j = (int)(lightSize);
			
			if(lightType == PointLight.TYPE_POINT_LIGHT) {
//				Initialize the temporary X-, Y-, Z- and distance falloff variables of the current point light:
				final float pointLightX = this.lights[i + PointLight.RELATIVE_OFFSET_OF_POINT_LIGHT_POSITION_POINT_IN_LIGHTS + 0];
				final float pointLightY = this.lights[i + PointLight.RELATIVE_OFFSET_OF_POINT_LIGHT_POSITION_POINT_IN_LIGHTS + 1];
				final float pointLightZ = this.lights[i + PointLight.RELATIVE_OFFSET_OF_POINT_LIGHT_POSITION_POINT_IN_LIGHTS + 2];
				final float pointLightDistanceFalloff = this.lights[i + PointLight.RELATIVE_OFFSET_OF_POINT_LIGHT_DISTANCE_FALLOFF_SCALAR_IN_LIGHTS];
				
//				Calculate the delta values between the current light and the surface intersection point of the shape:
				float dx = pointLightX - surfaceIntersectionX;
				float dy = pointLightY - surfaceIntersectionY;
				float dz = pointLightZ - surfaceIntersectionZ;
				
//				Calculate the length reciprocal of the vector produced by the delta values:
				final float lengthReciprocal = 1.0F / sqrt(dx * dx + dy * dy + dz * dz);
				
//				Multiply the delta values with the length reciprocal to normalize it:
				dx *= lengthReciprocal;
				dy *= lengthReciprocal;
				dz *= lengthReciprocal;
				
//				Calculate the shading as the maximum value of 0.0 and the dot product of the delta vector and the surface normal vector, then add it to the shading variable:
				shading += dx * surfaceNormalX + dy * surfaceNormalY + dz * surfaceNormalZ;
			}
		}
		
//		Update the shading variable to be between 0.1 and 1.0:
		shading = max(min(shading, 1.0F), 0.1F);
		
		return shading;
	}
	
	private float doGetIntersection() {
//		Initialize the index and offset values:
		final int index = getGlobalId();
		final int intersectionOffset = index * Constants.SIZE_OF_INTERSECTION_IN_INTERSECTIONS;
		final int rayOffset = index * Constants.SIZE_OF_RAY_IN_RAYS;
		
//		Initialize offset to closest shape:
		int shapeClosestOffset = -1;
		
//		Initialize distance to closest shape:
		float shapeClosestDistance = Constants.MAXIMUM_DISTANCE;
		
//		Initialize the ray values (origin and direction):
		final float rayOriginX = this.rays[rayOffset + Constants.RELATIVE_OFFSET_OF_RAY_ORIGIN_POINT_IN_RAYS + 0];
		final float rayOriginY = this.rays[rayOffset + Constants.RELATIVE_OFFSET_OF_RAY_ORIGIN_POINT_IN_RAYS + 1];
		final float rayOriginZ = this.rays[rayOffset + Constants.RELATIVE_OFFSET_OF_RAY_ORIGIN_POINT_IN_RAYS + 2];
		final float rayDirectionX = this.rays[rayOffset + Constants.RELATIVE_OFFSET_OF_RAY_DIRECTION_VECTOR_IN_RAYS + 0];
		final float rayDirectionY = this.rays[rayOffset + Constants.RELATIVE_OFFSET_OF_RAY_DIRECTION_VECTOR_IN_RAYS + 1];
		final float rayDirectionZ = this.rays[rayOffset + Constants.RELATIVE_OFFSET_OF_RAY_DIRECTION_VECTOR_IN_RAYS + 2];
		
//		Reset the float array intersections, so we can perform a new intersection test:
		this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SHAPE_OFFSET_SCALAR_IN_INTERSECTIONS] = -1.0F;
		this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_DISTANCE_SCALAR_IN_INTERSECTIONS] = Constants.MAXIMUM_DISTANCE;
		
		for(int i = 0, shapeOffset = this.shapeIndices[i]; i < this.shapeIndicesLength && shapeOffset >= 0; i++, shapeOffset = this.shapeIndices[i]) {
//			Initialize the temporary type and size variables of the current shape:
			final float shapeType = this.shapes[shapeOffset + Shape.RELATIVE_OFFSET_OF_SHAPE_TYPE_SCALAR_IN_SHAPES];
//			final float shapeSize = this.shapes[shapeOffset + Shape.RELATIVE_OFFSET_OF_SHAPE_SIZE_SCALAR_IN_SHAPES];
			
//			Initialize the shape distance to the maximum value:
			float shapeDistance = Constants.MAXIMUM_DISTANCE;
			
			if(shapeType == Plane.TYPE_PLANE) {
//				Update the shape distance based on the intersected plane:
				shapeDistance = doGetIntersectionForPlane(rayOriginX, rayOriginY, rayOriginZ, rayDirectionX, rayDirectionY, rayDirectionZ, shapeOffset);
			}
			
			if(shapeType == Sphere.TYPE_SPHERE) {
//				Update the shape distance based on the intersected sphere:
				shapeDistance = doGetIntersectionForSphere(rayOriginX, rayOriginY, rayOriginZ, rayDirectionX, rayDirectionY, rayDirectionZ, shapeOffset);
			}
			
			if(shapeType == Triangle.TYPE_TRIANGLE) {
//				Update the shape distance based on the intersected triangle:
				shapeDistance = doGetIntersectionForTriangle(rayOriginX, rayOriginY, rayOriginZ, rayDirectionX, rayDirectionY, rayDirectionZ, shapeOffset);
			}
			
			if(shapeDistance > 0.0F && shapeDistance < shapeClosestDistance) {
//				Update the distance to and the offset of the closest shape:
				shapeClosestDistance = shapeDistance;
				shapeClosestOffset = shapeOffset;
			}
		}
		
		if(shapeClosestOffset > -1) {
//			Update the intersections array with values found:
			this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SHAPE_OFFSET_SCALAR_IN_INTERSECTIONS] = shapeClosestOffset;
			this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_DISTANCE_SCALAR_IN_INTERSECTIONS] = shapeClosestDistance;
			this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_INTERSECTION_POINT_IN_INTERSECTIONS + 0] = rayOriginX + rayDirectionX * shapeClosestDistance;
			this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_INTERSECTION_POINT_IN_INTERSECTIONS + 1] = rayOriginY + rayDirectionY * shapeClosestDistance;
			this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_INTERSECTION_POINT_IN_INTERSECTIONS + 2] = rayOriginZ + rayDirectionZ * shapeClosestDistance;
			
			if(this.shapes[shapeClosestOffset + Shape.RELATIVE_OFFSET_OF_SHAPE_TYPE_SCALAR_IN_SHAPES] == Plane.TYPE_PLANE) {
//				Update the intersections array with the surface normal of the intersected plane:
				doUpdateSurfaceNormalForPlane(intersectionOffset, shapeClosestOffset);
			}
			
			if(this.shapes[shapeClosestOffset + Shape.RELATIVE_OFFSET_OF_SHAPE_TYPE_SCALAR_IN_SHAPES] == Sphere.TYPE_SPHERE) {
//				Update the intersections array with the surface normal of the intersected sphere:
				doUpdateSurfaceNormalForSphere(intersectionOffset, shapeClosestOffset);
			}
			
			if(this.shapes[shapeClosestOffset + Shape.RELATIVE_OFFSET_OF_SHAPE_TYPE_SCALAR_IN_SHAPES] == Triangle.TYPE_TRIANGLE) {
//				Update the intersections array with the surface normal of the intersected triangle:
				doUpdateSurfaceNormalForTriangle(intersectionOffset, shapeClosestOffset);
			}
		}
		
		return shapeClosestDistance;
	}
	
	private float doGetIntersectionForPlane(final float rayOriginX, final float rayOriginY, final float rayOriginZ, final float rayDirectionX, final float rayDirectionY, final float rayDirectionZ, final int shapeOffset) {
//		Initialize a variable with the plane constant:
		final float planeConstant = -2.0F;
		
//		Initialize the temporary X-, Y- and Z- variables of the current plane:
		final float planeSurfaceNormalX = this.shapes[shapeOffset + Plane.RELATIVE_OFFSET_OF_PLANE_SURFACE_NORMAL_VECTOR_IN_SHAPES + 0];
		final float planeSurfaceNormalY = this.shapes[shapeOffset + Plane.RELATIVE_OFFSET_OF_PLANE_SURFACE_NORMAL_VECTOR_IN_SHAPES + 1];
		final float planeSurfaceNormalZ = this.shapes[shapeOffset + Plane.RELATIVE_OFFSET_OF_PLANE_SURFACE_NORMAL_VECTOR_IN_SHAPES + 2];
		
//		Calculate the dot product and its absolute version:
		final float dotProduct = rayDirectionX * planeSurfaceNormalX + rayDirectionY * planeSurfaceNormalY + rayDirectionZ * planeSurfaceNormalZ;
		final float dotProductAbsolute = abs(dotProduct);
		
//		Initialize the shape distance variable:
		float shapeDistance = 0.0F;
		
		if(dotProductAbsolute >= Constants.EPSILON) {
//			Calculate the distance:
			shapeDistance = (planeConstant - (rayOriginX * planeSurfaceNormalX + rayOriginY * planeSurfaceNormalY + rayOriginZ * planeSurfaceNormalZ)) / dotProduct;
		}
		
		return shapeDistance;
	}
	
	private float doGetIntersectionForSphere(final float rayOriginX, final float rayOriginY, final float rayOriginZ, final float rayDirectionX, final float rayDirectionY, final float rayDirectionZ, final int shapeOffset) {
//		Initialize the temporary X-, Y-, Z- and radius variables of the current sphere:
		final float sphereX = this.shapes[shapeOffset + Sphere.RELATIVE_OFFSET_OF_SPHERE_POSITION_POINT_IN_SHAPES + 0];
		final float sphereY = this.shapes[shapeOffset + Sphere.RELATIVE_OFFSET_OF_SPHERE_POSITION_POINT_IN_SHAPES + 1];
		final float sphereZ = this.shapes[shapeOffset + Sphere.RELATIVE_OFFSET_OF_SPHERE_POSITION_POINT_IN_SHAPES + 2];
		final float sphereRadius = this.shapes[shapeOffset + Sphere.RELATIVE_OFFSET_OF_SPHERE_RADIUS_SCALAR_IN_SHAPES];
		
//		Calculate the delta values between the current sphere and the origin of the camera:
		final float dx = sphereX - rayOriginX;
		final float dy = sphereY - rayOriginY;
		final float dz = sphereZ - rayOriginZ;
		
//		Calculate the dot product:
		final float b = dx * rayDirectionX + dy * rayDirectionY + dz * rayDirectionZ;
		
//		Calculate the discriminant:
		float discriminant = b * b - (dx * dx + dy * dy + dz * dz) + sphereRadius * sphereRadius;
		
//		Initialize the shape distance variable:
		float shapeDistance = 0.0F;
		
		if(discriminant >= 0.0F) {
//			Recalculate the discriminant:
			discriminant = sqrt(discriminant);
			
//			Calculate the distance:
			shapeDistance = b - discriminant;
			
			if(shapeDistance <= Constants.EPSILON) {
//				Recalculate the distance:
				shapeDistance = b + discriminant;
				
				if(shapeDistance <= Constants.EPSILON) {
//					We're too close to the shape, so we practically do not see it:
					shapeDistance = 0.0F;
				}
			}
		}
		
		return shapeDistance;
	}
	
	private float doGetIntersectionForTriangle(final float rayOriginX, final float rayOriginY, final float rayOriginZ, final float rayDirectionX, final float rayDirectionY, final float rayDirectionZ, final int shapeOffset) {
//		Initialize the shape distance variable to be returned:
		float shapeDistance = 0.0F;
		
//		Initialize the X-, Y- and Z-values of the A point of the triangle:
		final float triangleAX = this.shapes[shapeOffset + Triangle.RELATIVE_OFFSET_OF_TRIANGLE_A_POINT_IN_SHAPES + 0];
		final float triangleAY = this.shapes[shapeOffset + Triangle.RELATIVE_OFFSET_OF_TRIANGLE_A_POINT_IN_SHAPES + 1];
		final float triangleAZ = this.shapes[shapeOffset + Triangle.RELATIVE_OFFSET_OF_TRIANGLE_A_POINT_IN_SHAPES + 2];
		
//		Initialize the X-, Y- and Z-values of the B point of the triangle:
		final float triangleBX = this.shapes[shapeOffset + Triangle.RELATIVE_OFFSET_OF_TRIANGLE_B_POINT_IN_SHAPES + 0];
		final float triangleBY = this.shapes[shapeOffset + Triangle.RELATIVE_OFFSET_OF_TRIANGLE_B_POINT_IN_SHAPES + 1];
		final float triangleBZ = this.shapes[shapeOffset + Triangle.RELATIVE_OFFSET_OF_TRIANGLE_B_POINT_IN_SHAPES + 2];
		
//		Initialize the X-, Y- and Z-values of the C point of the triangle:
		final float triangleCX = this.shapes[shapeOffset + Triangle.RELATIVE_OFFSET_OF_TRIANGLE_C_POINT_IN_SHAPES + 0];
		final float triangleCY = this.shapes[shapeOffset + Triangle.RELATIVE_OFFSET_OF_TRIANGLE_C_POINT_IN_SHAPES + 1];
		final float triangleCZ = this.shapes[shapeOffset + Triangle.RELATIVE_OFFSET_OF_TRIANGLE_C_POINT_IN_SHAPES + 2];
		
//		Calculate the X-, Y- and Z-values of the first edge of the triangle:
		final float edge0X = triangleBX - triangleAX;
		final float edge0Y = triangleBY - triangleAY;
		final float edge0Z = triangleBZ - triangleAZ;
		
//		Calculate the X-, Y- and Z-values of the second edge of the triangle:
		final float edge1X = triangleCX - triangleAX;
		final float edge1Y = triangleCY - triangleAY;
		final float edge1Z = triangleCZ - triangleAZ;
		
//		Calculate the cross product between the ray direction and the second edge:
		final float pX = rayDirectionY * edge1Z - rayDirectionZ * edge1Y;
		final float pY = rayDirectionZ * edge1X - rayDirectionX * edge1Z;
		final float pZ = rayDirectionX * edge1Y - rayDirectionY * edge1X;
		
//		Calculate the determinant:
		final float determinant = edge0X * pX + edge0Y * pY + edge0Z * pZ;
		
		if(determinant != 0.0F) {
//			Calculate the reciprocal of the determinant:
			final float determinantReciprocal = 1.0F / determinant;
			
//			Calculate the direction between the ray origin and the triangle A point:
			final float vX = rayOriginX - triangleAX;
			final float vY = rayOriginY - triangleAY;
			final float vZ = rayOriginZ - triangleAZ;
			
//			Calculate the dot product and multiply it with the reciprocal of the determinant:
			final float u = (vX * pX + vY * pY + vZ * pZ) * determinantReciprocal;
			
			if(u >= 0.0F && u <= 1.0F) {
//				Calculate the cross product between the previously calculated direction (between the ray origin and the triangle A point) and the first edge:
				final float qX = vY * edge0Z - vZ * edge0Y;
				final float qY = vZ * edge0X - vX * edge0Z;
				final float qZ = vX * edge0Y - vY * edge0X;
				
//				Calculate the dot product and multiply it with the reciprocal of the determinant:
				final float v = (rayDirectionX * qX + rayDirectionY * qY + rayDirectionZ * qZ) * determinantReciprocal;
				
				if(v >= 0.0F && u + v <= 1.0F) {
//					Update the shape distance as the dot product multiplied with the reciprocal of the determinant:
					shapeDistance = (edge1X * qX + edge1Y * qY + edge1Z * qZ) * determinantReciprocal;
				}
			}
		}
		
		return shapeDistance;
	}
	
	private float doLength(final float[] vector, final int offset) {
		return sqrt(doLengthSquared(vector, offset));
	}
	
	private void doNormalize(final float[] vector, final int offset) {
		final float lengthReciprocal = 1.0F / doLength(vector, offset);
		
		vector[offset + 0] *= lengthReciprocal;
		vector[offset + 1] *= lengthReciprocal;
		vector[offset + 2] *= lengthReciprocal;
	}
	
	private void doPerformFrustumCulling() {
//		TODO: Implement View Frustum Culling here.
		
		final List<Shape> shapes = this.scene.getShapes();
		
		for(int i = 0; i < this.shapeIndicesLength; i++) {
			this.shapeIndices[i] = shapes.get(i).getIndex();
		}
	}
	
	private void doPerformSphericalTextureMapping(final int intersectionOffset, final int pixelOffset, final int shapeOffset) {
//		Initialize the variables with the position (the X-, Y- and Z-values) of the sphere:
		final float sphereX = this.shapes[shapeOffset + Sphere.RELATIVE_OFFSET_OF_SPHERE_POSITION_POINT_IN_SHAPES + 0];
		final float sphereY = this.shapes[shapeOffset + Sphere.RELATIVE_OFFSET_OF_SPHERE_POSITION_POINT_IN_SHAPES + 1];
		final float sphereZ = this.shapes[shapeOffset + Sphere.RELATIVE_OFFSET_OF_SPHERE_POSITION_POINT_IN_SHAPES + 2];
		
//		Initialize the variables with the surface intersection point (the X-, Y- and Z-values) of the sphere:
		final float surfaceIntersectionX = this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_INTERSECTION_POINT_IN_INTERSECTIONS + 0];
		final float surfaceIntersectionY = this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_INTERSECTION_POINT_IN_INTERSECTIONS + 1];
		final float surfaceIntersectionZ = this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_INTERSECTION_POINT_IN_INTERSECTIONS + 2];
		
//		Calculate the delta values between the position and the surface intersection point of the sphere:
		final float dx = sphereX - surfaceIntersectionX;
		final float dy = sphereY - surfaceIntersectionY;
		final float dz = sphereZ - surfaceIntersectionZ;
		
//		Calculate the length reciprocal of the delta values:
		final float lengthReciprocal = 1.0F / sqrt(dx * dx + dy * dy + dz * dz);
		
//		Calculate the distance values by multiplying the delta values with the reciprocal of the length (normalize):
		final float distanceX = dx * lengthReciprocal;
		final float distanceY = dy * lengthReciprocal;
		final float distanceZ = dz * lengthReciprocal;
		
//		Calculate the U- and V-values of the sphere on the surface intersection point:
		final float textureU = 0.5F + atan2(distanceX, distanceZ) / (2.0F * Constants.PI);
		final float textureV = 0.5F + asin(distanceY) / Constants.PI;
		
//		Calculate the X- and Y-values of the texture to be applied to the sphere on the surface intersection point:
		final int textureX = (int)(this.textureWidth * ((textureU + 1.0F) * 0.5F));
		final int textureY = (int)(this.textureHeight * ((textureV + 1.0F) * 0.5F));
		
//		Calculate the index of the RGB-value and fetch the RGB-value using said index:
		final int textureIndex = textureY * this.textureWidth + textureX;
		final int textureRGB = this.texture[textureIndex];
		
//		Update the RGB-values of the pixels array:
		this.pixels[pixelOffset + 0] = doToR(textureRGB);
		this.pixels[pixelOffset + 1] = doToG(textureRGB);
		this.pixels[pixelOffset + 2] = doToB(textureRGB);
	}
	
	private void doUpdate() {
//		Calculate the movement based on some velocity, calculated as the distance moved per second:
		final float velocity = 250.0F;
		final float movement = this.fPSCounter.getFrameTimeMillis() / 1000.0F * velocity;
		
		if(this.isKeyPressed[KeyEvent.VK_A]) {
			this.camera.move(-movement, 0.0F, 0.0F);
		}
		
		if(this.isKeyPressed[KeyEvent.VK_D]) {
			this.camera.move(movement, 0.0F, 0.0F);
		}
		
		if(this.isKeyPressed[KeyEvent.VK_E] && this.isPrintingExecutionMode.compareAndSet(false, true)) {
			System.out.printf("ExecutionMode: %s%n", getExecutionMode());
		} else if(!this.isKeyPressed[KeyEvent.VK_E]) {
			this.isPrintingExecutionMode.compareAndSet(true, false);
		}
		
		if(this.isKeyPressed[KeyEvent.VK_ESCAPE] && this.isTerminationRequested.compareAndSet(false, true)) {
			this.isRunning.set(false);
		} else if(!this.isKeyPressed[KeyEvent.VK_ESCAPE]) {
			this.isTerminationRequested.compareAndSet(true, false);
		}
		
		if(this.isKeyPressed[KeyEvent.VK_S]) {
			this.camera.move(0.0F, 0.0F, movement);
		}
		
		if(this.isKeyPressed[KeyEvent.VK_T] && this.isTogglingExecutionMode.compareAndSet(false, true)) {
			setExecutionMode(getExecutionMode() == EXECUTION_MODE.GPU ? EXECUTION_MODE.JTP : EXECUTION_MODE.GPU);
		} else if(!this.isKeyPressed[KeyEvent.VK_T]) {
			this.isTogglingExecutionMode.compareAndSet(true, false);
		}
		
		if(this.isKeyPressed[KeyEvent.VK_W]) {
			this.camera.move(0.0F, 0.0F, -movement);
		}
		
//		Perform View Frustum Culling:
		doPerformFrustumCulling();
	}
	
	private void doUpdateSurfaceNormalForPlane(final int intersectionOffset, final int shapeOffset) {
//		Initialize variables with the surface normal vector:
		final float surfaceNormalX = this.shapes[shapeOffset + Plane.RELATIVE_OFFSET_OF_PLANE_SURFACE_NORMAL_VECTOR_IN_SHAPES + 0];
		final float surfaceNormalY = this.shapes[shapeOffset + Plane.RELATIVE_OFFSET_OF_PLANE_SURFACE_NORMAL_VECTOR_IN_SHAPES + 1];
		final float surfaceNormalZ = this.shapes[shapeOffset + Plane.RELATIVE_OFFSET_OF_PLANE_SURFACE_NORMAL_VECTOR_IN_SHAPES + 2];
		
//		Update the intersections array with the surface normal vector:
		this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_NORMAL_VECTOR_IN_INTERSECTIONS + 0] = surfaceNormalX;
		this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_NORMAL_VECTOR_IN_INTERSECTIONS + 1] = surfaceNormalY;
		this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_NORMAL_VECTOR_IN_INTERSECTIONS + 2] = surfaceNormalZ;
	}
	
	private void doUpdateSurfaceNormalForSphere(final int intersectionOffset, final int shapeOffset) {
//		Initialize variables with the position of the sphere:
		final float sphereX = this.shapes[shapeOffset + Sphere.RELATIVE_OFFSET_OF_SPHERE_POSITION_POINT_IN_SHAPES + 0];
		final float sphereY = this.shapes[shapeOffset + Sphere.RELATIVE_OFFSET_OF_SPHERE_POSITION_POINT_IN_SHAPES + 1];
		final float sphereZ = this.shapes[shapeOffset + Sphere.RELATIVE_OFFSET_OF_SPHERE_POSITION_POINT_IN_SHAPES + 2];
		
//		Initialize variables with the delta values between the surface intersection point and the center of the sphere:
		final float dx = this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_INTERSECTION_POINT_IN_INTERSECTIONS + 0] - sphereX;
		final float dy = this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_INTERSECTION_POINT_IN_INTERSECTIONS + 1] - sphereY;
		final float dz = this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_INTERSECTION_POINT_IN_INTERSECTIONS + 2] - sphereZ;
		
//		Calculate the length of the delta vector:
		final float length = sqrt(dx * dx + dy * dy + dz * dz);
		
//		Initialize variables with the surface normal vector:
		float surfaceNormalX = 0.0F;
		float surfaceNormalY = 0.0F;
		float surfaceNormalZ = 0.0F;
		
		if(length > 0.0F) {
//			//Calculate the length reciprocal:
			final float lengthReciprocal = 1.0F / length;
			
//			Set the surface normal vector to the delta vector multiplied with the length reciprocal:
			surfaceNormalX = dx * lengthReciprocal;
			surfaceNormalY = dy * lengthReciprocal;
			surfaceNormalZ = dz * lengthReciprocal;
		}
		
//		Update the intersections array with the surface normal vector:
		this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_NORMAL_VECTOR_IN_INTERSECTIONS + 0] = surfaceNormalX;
		this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_NORMAL_VECTOR_IN_INTERSECTIONS + 1] = surfaceNormalY;
		this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_NORMAL_VECTOR_IN_INTERSECTIONS + 2] = surfaceNormalZ;
	}
	
	private void doUpdateSurfaceNormalForTriangle(final int intersectionOffset, final int shapeOffset) {
//		Initialize variables with the surface normal vector:
		final float surfaceNormalX = this.shapes[shapeOffset + Triangle.RELATIVE_OFFSET_OF_TRIANGLE_SURFACE_NORMAL_VECTOR_IN_SHAPES + 0];
		final float surfaceNormalY = this.shapes[shapeOffset + Triangle.RELATIVE_OFFSET_OF_TRIANGLE_SURFACE_NORMAL_VECTOR_IN_SHAPES + 1];
		final float surfaceNormalZ = this.shapes[shapeOffset + Triangle.RELATIVE_OFFSET_OF_TRIANGLE_SURFACE_NORMAL_VECTOR_IN_SHAPES + 2];
		
//		Update the intersections array with the surface normal vector:
		this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_NORMAL_VECTOR_IN_INTERSECTIONS + 0] = surfaceNormalX;
		this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_NORMAL_VECTOR_IN_INTERSECTIONS + 1] = surfaceNormalY;
		this.intersections[intersectionOffset + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_NORMAL_VECTOR_IN_INTERSECTIONS + 2] = surfaceNormalZ;
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	
	private static float doDotProduct(final float[] vector0, final int offset0, final float[] vector1, final int offset1) {
		return vector0[offset0] * vector1[offset1] + vector0[offset0 + 1] * vector1[offset1 + 1] + vector0[offset0 + 2] * vector1[offset1 + 2];
	}
	
	private static float doLengthSquared(final float[] vector, final int offset) {
		return doDotProduct(vector, offset, vector, offset);
	}
	
	private static float[] doCreateIntersections(final int length) {
		final float[] intersections = new float[length * Constants.SIZE_OF_INTERSECTION_IN_INTERSECTIONS];
		
		for(int i = 0; i < intersections.length; i += Constants.SIZE_OF_INTERSECTION_IN_INTERSECTIONS) {
			intersections[i + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SHAPE_OFFSET_SCALAR_IN_INTERSECTIONS] = -1.0F;
			intersections[i + Constants.RELATIVE_OFFSET_OF_INTERSECTION_DISTANCE_SCALAR_IN_INTERSECTIONS] = Constants.MAXIMUM_DISTANCE;
			intersections[i + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_INTERSECTION_POINT_IN_INTERSECTIONS + 0] = 0.0F;
			intersections[i + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_INTERSECTION_POINT_IN_INTERSECTIONS + 1] = 0.0F;
			intersections[i + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_INTERSECTION_POINT_IN_INTERSECTIONS + 2] = 0.0F;
			intersections[i + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_NORMAL_VECTOR_IN_INTERSECTIONS + 0] = 0.0F;
			intersections[i + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_NORMAL_VECTOR_IN_INTERSECTIONS + 1] = 0.0F;
			intersections[i + Constants.RELATIVE_OFFSET_OF_INTERSECTION_SURFACE_NORMAL_VECTOR_IN_INTERSECTIONS + 2] = 0.0F;
		}
		
		return intersections;
	}
	
	private static float[] doCreatePixels(final int length) {
		final float[] pixels = new float[length * Constants.SIZE_OF_PIXEL_IN_PIXELS];
		
		for(int i = 0; i < pixels.length; i += Constants.SIZE_OF_PIXEL_IN_PIXELS) {
			pixels[i + 0] = 0.0F;
			pixels[i + 1] = 0.0F;
			pixels[i + 2] = 0.0F;
		}
		
		return pixels;
	}
	
	private static float[] doCreateRays(final int length) {
		return new float[length * Constants.SIZE_OF_RAY_IN_RAYS];
	}
	
	private static int doToB(final int rGB) {
		return (rGB >> 0) & 0xFF;
	}
	
	private static int doToG(final int rGB) {
		return (rGB >> 8) & 0xFF;
	}
	
	private static int doToR(final int rGB) {
		return (rGB >> 16) & 0xFF;
	}
	
	private static int doToRGB(final int r, final int g, final int b) {
		return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | ((b & 0xFF) << 0);
	}
	
	private static int[] doToRGB(final BufferedImage bufferedImage) {
		final WritableRaster writableRaster = bufferedImage.getRaster();
		
		final DataBuffer dataBuffer = writableRaster.getDataBuffer();
		
		final DataBufferInt dataBufferInt = DataBufferInt.class.cast(dataBuffer);
		
		final int[] rGB = dataBufferInt.getData();
		
		return rGB;
	}
	
	private static JFrame doCreateJFrame(final BufferedImage bufferedImage, final Camera camera, final FPSCounter fPSCounter) {
		final
		JFrame jFrame = new JFrame();
		jFrame.setContentPane(doCreateJPanel(bufferedImage, camera, fPSCounter));
		jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		jFrame.setFocusTraversalKeysEnabled(false);
		jFrame.setIgnoreRepaint(true);
		jFrame.setSize(bufferedImage.getWidth(), bufferedImage.getHeight());
		jFrame.setLocationRelativeTo(null);
		jFrame.setTitle("OpenRC - OpenCL Ray Caster");
		jFrame.setVisible(true);
		jFrame.createBufferStrategy(2);
		jFrame.repaint();
		
		return jFrame;
	}
	
	private static JPanel doCreateJPanel(final BufferedImage bufferedImage, final Camera camera, final FPSCounter fPSCounter) {
		final
		JPanel jPanel = new JBufferedImagePanel(bufferedImage, camera, fPSCounter);
		jPanel.setBorder(BorderFactory.createLineBorder(Color.WHITE, 1));
		jPanel.setLayout(new AbsoluteLayoutManager());
		jPanel.setPreferredSize(new Dimension(bufferedImage.getWidth(), bufferedImage.getHeight()));
		
		return jPanel;
	}
}