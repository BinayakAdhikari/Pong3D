// This code example is created for educational purpose
// by Thorsten Thormaehlen (contact: www.thormae.de).
// It is distributed without any warranty.

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.imageio.ImageIO;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import javax.swing.JFrame;

import com.jogamp.opengl.util.FPSAnimator;

class Renderer {

    private GLU glu = new GLU();

    public float t = 0.0f;
    int texID = 0;

    public void init(GLAutoDrawable d) {
        GL2 gl = d.getGL().getGL2(); // get the OpenGL 2 graphics context
        gl.glEnable(GL2.GL_DEPTH_TEST);
        texID = loadTexture(d, "textures/intersteller.png");

    }

    public void resize(GLAutoDrawable d, int w, int h) {
        GL2 gl = d.getGL().getGL2(); // get the OpenGL 2 graphics context
        gl.glViewport(0, 0, w, h);
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        glu.gluPerspective (30.0, (float)w/(float)h, 0.1, 50.0);
    }

    public void display(GLAutoDrawable d) {
        GL2 gl = d.getGL().getGL2();  // get the OpenGL 2 graphics context
        gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();
        // set camera
        glu.gluLookAt(8.0, -2.0, 5.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0);
        // draw scene
        gl.glRotatef(t, 0.0f, 0.0f, 1.0f);
        drawTexturedCube(d);
    }

    // returns a valid textureID on success, otherwise 0
    private int loadTexture(GLAutoDrawable d, String filename) {
        GL2 gl = d.getGL().getGL2(); // get the OpenGL 2 graphics context

        int width;
        int height;
        int level = 0;
        int border = 0;

        try{
            // open file
            FileInputStream fileInputStream = new FileInputStream(new File(filename));

            // read image
            BufferedImage bufferedImage = ImageIO.read(fileInputStream);
            fileInputStream.close();

            width = bufferedImage.getWidth();
            height = bufferedImage.getHeight();

            // convert image to ByteBuffer
            int[] pixelIntData = new int[width * height];
            bufferedImage.getRGB(0, 0, width, height, pixelIntData, 0, width);
            ByteBuffer buffer = ByteBuffer.allocateDirect(pixelIntData.length * 4);
            buffer.order(ByteOrder.nativeOrder());
            // Unpack the data, each integer into 4 bytes of the ByteBuffer.
            // Also we need to vertically flip the image because the image origin
            // in OpenGL is the lower-left corner.
            for(int y=0; y < height; y++) {
                int k = (height-1-y) * width;
                for(int x=0; x < width; x++) {
                    buffer.put((byte)(pixelIntData[k]>>> 16));
                    buffer.put((byte)(pixelIntData[k]>>> 8));
                    buffer.put((byte)(pixelIntData[k]));
                    buffer.put((byte)(pixelIntData[k]>>> 24));
                    k++;
                }
            }
            buffer.rewind();



            // data is aligned in byte order
            gl.glPixelStorei(GL2.GL_UNPACK_ALIGNMENT, 1);

            //request textureID
            final int[] textureID = new int[1];
            gl.glGenTextures( 1, textureID, 0);

            // bind texture
            gl.glBindTexture(GL2.GL_TEXTURE_2D, textureID[0]);

            //define how to filter the texture (important but ignore for now)
            gl.glTexParameteri (GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
            gl.glTexParameteri (GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);

            //texture colors should replace the original color values
            gl.glTexEnvf(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_REPLACE); //GL_MODULATE

            // specify the 2D texture map
            gl.glTexImage2D(GL2.GL_TEXTURE_2D, level, GL2.GL_RGB, width, height, border, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, buffer);

            return textureID[0];
        } catch( FileNotFoundException e) {
            System.out.println("Can not find texture data file " + filename);
        } catch(IOException e) {
            e.printStackTrace( );
        }
        return 0;
    }

    private void drawTexturedCube(GLAutoDrawable d) {
        GL2 gl = d.getGL().getGL2();  // get the OpenGL 2 graphics context
        gl.glEnable(GL2.GL_TEXTURE_2D);
        gl.glBindTexture(GL2.GL_TEXTURE_2D, texID);
        gl.glColor3f(1.0f,0.0f,0.0f);
        gl.glBegin(GL2.GL_POLYGON); // three
        gl.glTexCoord2f(0.25f,0.50f); gl.glVertex3f(-1.0f, 1.0f, 1.0f);
        gl.glTexCoord2f(0.25f,0.25f); gl.glVertex3f(-1.0f,-1.0f, 1.0f);
        gl.glTexCoord2f(0.50f,0.25f); gl.glVertex3f( 1.0f,-1.0f, 1.0f);
        gl.glTexCoord2f(0.50f,0.50f); gl.glVertex3f( 1.0f, 1.0f, 1.0f);
        gl.glEnd();
        gl.glColor3f(1.0f,1.0f,0.0f);
        gl.glBegin(GL2.GL_POLYGON); // five
        gl.glTexCoord2f(0.00f,0.50f); gl.glVertex3f(-1.0f, 1.0f,-1.0f);
        gl.glTexCoord2f(0.00f,0.25f); gl.glVertex3f(-1.0f,-1.0f,-1.0f);
        gl.glTexCoord2f(0.25f,0.25f); gl.glVertex3f(-1.0f,-1.0f, 1.0f);
        gl.glTexCoord2f(0.25f,0.50f); gl.glVertex3f(-1.0f, 1.0f, 1.0f);
        gl. glEnd();
        gl.glColor3f(0.0f,0.0f,1.0f);
        gl.glBegin(GL2.GL_POLYGON); // two
        gl.glTexCoord2f(0.50f,0.50f); gl.glVertex3f( 1.0f, 1.0f, 1.0f);
        gl.glTexCoord2f(0.50f,0.25f); gl.glVertex3f( 1.0f,-1.0f, 1.0f);
        gl.glTexCoord2f(0.75f,0.25f); gl.glVertex3f( 1.0f,-1.0f,-1.0f);
        gl.glTexCoord2f(0.75f,0.50f); gl.glVertex3f( 1.0f, 1.0f,-1.0f);
        gl.glEnd();
        gl.glColor3f(1.0f,1.0f,1.0f);
        gl.glBegin(GL2.GL_POLYGON); // six
        gl.glTexCoord2f(0.25f,0.75f); gl.glVertex3f(-1.0f, 1.0f,-1.0f);
        gl.glTexCoord2f(0.25f,0.50f); gl.glVertex3f(-1.0f, 1.0f, 1.0f);
        gl.glTexCoord2f(0.50f,0.50f); gl.glVertex3f( 1.0f, 1.0f, 1.0f);
        gl.glTexCoord2f(0.50f,0.75f); gl.glVertex3f( 1.0f, 1.0f,-1.0f);
        gl.glEnd();
        gl.glColor3f(0.0f,1.0f,1.0f);
        gl.glBegin(GL2.GL_POLYGON); // one
        gl.glTexCoord2f(0.25f,0.25f); gl.glVertex3f(-1.0f, -1.0f, 1.0f);
        gl.glTexCoord2f(0.25f,0.00f); gl.glVertex3f(-1.0f, -1.0f,-1.0f);
        gl.glTexCoord2f(0.50f,0.00f); gl.glVertex3f( 1.0f, -1.0f,-1.0f);
        gl.glTexCoord2f(0.50f,0.25f); gl.glVertex3f( 1.0f, -1.0f, 1.0f);
        gl.glEnd();
        gl.glColor3f(0.0f,1.0f,0.0f);
        gl.glBegin(GL2.GL_POLYGON); //four
        gl.glTexCoord2f(0.75f,0.50f); gl.glVertex3f( 1.0f, 1.0f,-1.0f);
        gl.glTexCoord2f(0.75f,0.25f); gl.glVertex3f( 1.0f,-1.0f,-1.0f);
        gl.glTexCoord2f(1.00f,0.25f); gl.glVertex3f(-1.0f,-1.0f,-1.0f);
        gl.glTexCoord2f(1.00f,0.50f); gl.glVertex3f(-1.0f, 1.0f,-1.0f);
        gl.glEnd();
        gl.glDisable(GL2.GL_TEXTURE_2D);
    }


}

class MyGui1 extends JFrame implements GLEventListener {

    private Renderer renderer;

    public void createGUI() {
        setTitle("Texture Demo");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        GLProfile glp = GLProfile.getDefault();
        GLCapabilities caps = new GLCapabilities(glp);
        GLCanvas canvas = new GLCanvas(caps);
        setSize(320, 320);

        getContentPane().add(canvas);
        final FPSAnimator ani = new FPSAnimator(canvas, 60, true);
        canvas.addGLEventListener(this);
        setVisible(true);
        renderer = new Renderer();

        ani.start();
    }

    @Override
    public void init(GLAutoDrawable d) {
        renderer.init(d);
    }

    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int width, int height) {
        renderer.resize(d, width, height);
    }

    @Override
    public void display(GLAutoDrawable d) {
        float offset = 1.0f;
        renderer.t += offset;
        renderer.display(d);
    }

    @Override
    public void dispose(GLAutoDrawable d) {
    }

}

public class Texture {
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                MyGui1 myGUI = new MyGui1();
                myGUI.createGUI();
            }
        });
    }
}