import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Random;

import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.nio.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main {

    //Blueprint for a smoke particle
    static class Particle {
        double x, y;
        double vx, vy;
        double radius;

        int age;
        int lifetime;

        Random random;

        Particle(Random random, int width, int height) {
            this.random = random;
            reset(width, height);
        }

        //Give the particle a new starting position
        void reset(int width, int height) {
            x = width * 0.35 + random.nextDouble() * width * 0.3;
            y = height - random.nextDouble() * 100;
            vx = (random.nextDouble() - 0.5) * 0.8;
            vy = 0;
            radius = 5;
            age = 0;
            lifetime = 150 + random.nextInt(150);
        }

        //Update particle once per frame
        void update(int width, int height) {
            //age++;
            //Randomly change horizontal velocity
            vx += (random.nextDouble() - 0.5) * 0.06;
            //Reduce sideways movement gradually
            vx *= 0.99f;
            //Slowly accelerate upward
            //vy -= 0.002;
            //Move the particle
            vy-=0.0081;
            //Make the particle grow slightly
            //Respawn when it gets too old or leaves the screen
            if (age >= lifetime || y < -height/2) {
                //reset(width, height);
                vy=0;
            }
            if (x >= width/2 || x<= -width/2) {
                vx = -x*0.0001;
            }

            x += vx;
            y += vy;
        }

        //Drawing the particle
        void draw() {

            float lifeProgress = (float) age / lifetime;
            float alpha = 1.0f - lifeProgress;
            glPointSize((float) radius * 2);
            float glX = (float) (2.0 * x / width);
            float glY = (float) (2.0 * y / height);
            glVertex2f(glX, glY);
        }





    }

    ArrayList<Particle> particles = new ArrayList<>();
    Random random = new Random();

    public Main() {
        //Create 100 particles
        for (int i = 0; i < 4000; i++) {
            particles.add(
                    new Particle(random, 800, 600)
            );
        }
    }

    void updateParticles() {
        for (Particle p : particles) {
            p.update(width, height);
            p.draw();
        }
        //Check every pair of particles
        for (int i = 0; i < particles.size(); i++) {
            for (int j = i + 1; j < particles.size(); j++) {
                Particle a = particles.get(i);
                Particle b = particles.get(j);
                checkCollision(a,b);
            }
        }
        //Draw all particles
        for (Particle p : particles) {
            p.draw();
        }
    }

    void checkCollision(Particle a, Particle b) {
        //Finding distance between the particles
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double distanceSquared = dx * dx + dy * dy;

        //Find out how close they can get before touching
        double minDistance = a.radius + b.radius;

        //If they are too far apart, do nothing
        if (distanceSquared >= minDistance * minDistance) {
            return;
        }
        //Calculate the actual distance
        double distance = Math.sqrt(distanceSquared);

        //Avoid dividing by zero if both centres are identical
        if (distance < 0.0001) {
            return;
        }

        //Find the direction from particle a to particle b
        double nx = dx / distance;
        double ny = dy / distance;

        //Push particles apart so they no longer overlap
        double overlap = (minDistance - distance) * 0.3;

        a.vx -= nx * overlap / 2;
        a.vy -= ny * overlap / 2;

        b.vx += nx * overlap / 2;
        b.vy += ny * overlap /2;

        //Work out whether the particles are moving towards each other
        double relativeVelocity = (b.vx -a.vx) * nx + (b.vy - a.vy) * ny;

        //Only bounce if they are approaching each other
        if (relativeVelocity < 0) {
            double impulse = -relativeVelocity;
            a.vx -= impulse * nx;
            a.vy -= impulse * ny;
            b.vx += impulse * nx;
            b.vy += impulse * ny;

        }
    }

    private long window;
    public static int width, height;

    public void run() {
        init();
        loop();

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        glfwTerminate();
        glfwSetErrorCallback(null).free();

    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        // Configure GLFW
        glfwDefaultWindowHints(); // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable

        // Create the window
        window = glfwCreateWindow(900, 900, "SimSim!", NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create the GLFW window");

        // Setup a key callback. It will be called every time a key is pressed, repeated or released.
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE)
                glfwSetWindowShouldClose(window, true); // We will detect this in the rendering loop
        });

        // Get the thread stack and push a new frame
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1); // int*
            IntBuffer pHeight = stack.mallocInt(1); // int*

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight);
            height = pHeight.get(0);
            width = pWidth.get(0);
            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            // Center the window
            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        } // the stack frame is popped automatically

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);
        // Enable v-sync
        glfwSwapInterval(1);

        // Make the window visible
        glfwShowWindow(window);
    }

    private void loop() {
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.
        GL.createCapabilities();
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Set the clear color
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer

            glColor4f(0.5f, 0.8f, 1.0f,0.5f);
            glPointSize(10f);
            glBegin(GL_POINTS);
            updateParticles();
            glEnd();

            glfwSwapBuffers(window);
            glfwPollEvents();

        }
    }

    public static void main(String[] args) {
        new Main().run();

    }

}


