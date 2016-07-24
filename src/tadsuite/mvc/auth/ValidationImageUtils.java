package tadsuite.mvc.auth;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

import javax.imageio.ImageIO;

import tadsuite.mvc.core.MvcRequest;

public class ValidationImageUtils {
	 
    
    private static void drawImage(String code, int width, int height, int backgroundColorR, int backgroundColorG, int backgroundColorB, String[] fontArray, OutputStream output) throws IOException {
    	
		Random random = new Random();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        int distance = (int)Math.round(width/(code.length()+1));
        Graphics2D graphic = (Graphics2D) image.getGraphics();
        graphic.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphic.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    	graphic.setColor(new Color(backgroundColorR, backgroundColorG, backgroundColorB));
        graphic.fillRect(0,0,image.getWidth(),image.getHeight());
        graphic.setColor(new Color(random.nextInt(60)+60,random.nextInt(60)+60,random.nextInt(60)+60)); //d.setColor(new Color(random.nextInt(100)+130,random.nextInt(100)+130,random.nextInt(100)+130));//
    	
        int x = 2;
        for (int i = 0; i < code.length(); i++) {
        	graphic.setFont(new Font(fontArray[random.nextInt(fontArray.length)],Font.ITALIC | Font.BOLD, (int)Math.round(height/1.2)));
            double theta=Math.random()-0.5;
            int y=height-5;
            graphic.rotate(theta, x, y);
            graphic.drawString(code.substring(i, i+1), x, y-5);
            graphic.rotate(0-theta, x, y);
            x = x + distance;
        }
        //graphic.setColor(new Color(0xcc, 0xcc, 0xcc));
        //noise lines
        for (int i = 0; i < 2; i++) {
        	int randomA=random.nextInt(image.getHeight());
        	int randomB=random.nextInt(image.getHeight());
        	//graphic.setColor(new Color(random.nextInt(60)+60,random.nextInt(60)+60,random.nextInt(60)+60));
        	graphic.drawLine(0, randomA, width, randomB);
        	graphic.drawLine(0, randomA+1, width, randomB+1);
        	//graphic.drawLine(0, randomA+2, width, randomB+2);
        }
        for (int i = 0; i < 2; i++) {
        	int randomA=random.nextInt(image.getWidth());
        	int randomB=random.nextInt(image.getWidth());
        	//graphic.setColor(new Color(random.nextInt(60)+60,random.nextInt(60)+60,random.nextInt(60)+60));
        	graphic.drawLine(randomA, 0, randomB, height);
        	graphic.drawLine(randomA+1, 0, randomB+1, height);
        	//graphic.drawLine(randomA+2, 0, randomB+2, height);
        	//graphic.drawLine(randomA+3, 0, randomB+3, height);
        }
        graphic.dispose();
        
        try {
        	ImageIO.write(image,"png",output);
        } catch (Exception e) {
        	//有时客户端提前断开连接
        }
    }
    
    public static void generate(String sessionName, int charNum, int width, int height, int backgroundColorR, int backgroundColorG, int backgroundColorB, MvcRequest request){
		String valCode="abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNOPQRSTUVWXYZ23456789"; //"abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNOPQRSTUVWXYZ23456789!@#%&+="
		Random random = new Random();
        String code = "";
        for (int i = 0; i < charNum; i++) {
           code = code + valCode.charAt(random.nextInt(valCode.length()-1));
        }
        request.sessionWrite(sessionName, code);
        String[] fontArray=new String[] {"微软雅黑"};//new String[] {"微软雅黑"};//,  "Bauhaus Hv BT", "BroadwayEngraved BT", "Lucida Calligraphy"
        try {
			drawImage(code, width, height, backgroundColorR, backgroundColorG, backgroundColorB, fontArray, request.getResponse().getOutputStream());
		} catch (IOException e) {
			e.printStackTrace();
		}
     }
 
}
