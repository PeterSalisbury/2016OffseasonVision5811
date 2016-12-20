import org.opencv.imgproc.*;
import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.*;

import java.awt.*;
import java.awt.List;
import java.awt.image.BufferedImage;
import java.util.*;
import java.io.*;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;

import java.awt.image.DataBufferByte;
//"C:/Users/HP-Desktop/Documents/Peter/WPISampleTowerImage.jpg"

public class ProcessTest extends JPanel{
	
	static Mat originalImage;
	static MatOfPoint finalTarget; //a global variable representing the image after is is narrowed to one contour
	final static double horizontalCameraAngle = 60;
	final static double verticalCameraAngle = 34;
	static Point horizontalCorrectionGlobal;
	static Point targetCenterGlobal;
	static Point imageCenterGlobal;
	
	static double xDegreeCorrectionGlobal, yDegreeCorrectionGlobal;
	
	static Scalar white = new Scalar(255,255,255);
	static Scalar blue = new Scalar(255,0,0);
	static Scalar red = new Scalar(0,0,255);
	static Scalar yellow = new Scalar(0,255,255);
	
	public static BufferedImage grabImage(){ //********gets an image from the computer**********
		BufferedImage image;
		try{
			File input = new File("C:/Users/HP-Desktop/Documents/Peter/CompressedImage4.jpg"); //the file "TowerSampleOversaturatedTarget.png" ""C:/Users/HP-Desktop/Documents/Peter/CompressedImage4.jpg""
			image = ImageIO.read(input);
			System.loadLibrary( Core.NATIVE_LIBRARY_NAME);
			return image;
		}catch (Exception e) { //exception handling necessary
			System.out.println("Exception handled"); 
			image=null; 
			return image;
		}
	}
	
	public static Mat BufferedImageToMat(BufferedImage image){ //********changes a buffered image into a matrix******
		Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC3);
		byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
		mat.put(0, 0, data);
		System.out.println(image.getHeight() + " " + image.getWidth());
		originalImage = mat;
		return mat;
	}
	
	public static Mat colorFilter(Mat input){ //**********a simple rgb filter applied to the input matrix*********
		Mat binaryOut = new Mat(input.height(), input.width(), CvType.CV_8UC1); //a binary image to return after filtering
		int count = 0; //number of pixels that pass through the filter
		for(int i = 0; i<input.height();i++){
			for(int j = 0; j<input.width();j++){
				double[] data = input.get(i, j);
				double greenValue = data[1];
				double redValue = data[2];
				double[] high = new double[1]; high[0] = 255; //preset used to write to the binary image
				double[] low = new double[1]; low[0] = 0; //preset used to write to the binary image
				if (greenValue > 110 && redValue <100) { //***the filter itself***
					binaryOut.put(i, j, high); 
					count++;
				}else {
					binaryOut.put(i, j, low);
				}
			}
		}
		System.out.println(count); //displays the number of correctly colored pixels
		/*Mat image32S = new Mat();    //Experimental to get the findContours method below to work
		binaryOut.convertTo(image32S, CvType.CV_32SC1);
		System.out.println("converted");*/
		return binaryOut;
	}
	
	public static Mat contourDetection(Mat input){ //**********finding the target******************
		ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>(); //an expandable container for the contours
		Mat hierarchy = new Mat(); //a matrix that details the hierarchy of nested contours not used in this example
		System.out.println("about to find contours3"); //debug
		Imgproc.findContours(input, contours, hierarchy, Imgproc.RETR_LIST , Imgproc.CHAIN_APPROX_NONE ); 
		/* above is the method that finds the contours. The last 2 arguments can be adjusted to get a more compressed description of the contours: 
		the last argument set to Imgproc.CHAIN_APPROX_SIMPLE returns a "connect the dots" description of the contour. 
		the preceding argument changes the specificity of nested contours. 
		More info at http://docs.opencv.org/master/d3/dc0/group__imgproc__shape.html#ga17ed9f5d79ae97bd4c7cf18403e1689a */
		
		System.out.println(contours.size()); //the number of contours found
		
		long largestContour = 0; int indexLargest = 0; //this loop finds the largest contour by perimeter
		for(int i = 0; i < contours.size(); i++){             //a crude filter
			if(contours.get(i).total()>largestContour){
				indexLargest = i;
				largestContour = contours.get(i).total();
			}
		}
		System.out.println("Index: " + indexLargest + " Size: " + largestContour); //the largest contour's index and size
		
		Mat singleTargetMat = new Mat(input.height(), input.width(), CvType.CV_8UC1); //a new matrix
		double[] high = new double[1]; high[0] = 255; //presets used to write to the mat
		double[] low = new double[1]; low[0] = 0;
		for(int i = 0; i<input.height(); i++){   //sets the new matrix to black (brute force...)
			for(int j = 0; j< input.width();j++){
				singleTargetMat.put(i, j,low);
			}
		}
		
		for(int i = 0; i<contours.get(indexLargest).total(); i++){     //draw the pixels of the largest contour on the new black mat
			double[] pixelData = contours.get(indexLargest).get(i, 0); //grabs the contents of the matrix, a point
			System.out.println("X: " + pixelData[0] + " Y: " + pixelData[1]); //print pixeldata
			int pointX = (int) pixelData[1]; int pointY = (int) pixelData[0]; //turn x and y doubles into ints
			singleTargetMat.put(pointX, pointY,high); //turn these pixels white
		}
		finalTarget = contours.get(indexLargest); //fill a global variable for other methods to use
		return singleTargetMat;
	}
	
	public static Mat errorCalculation(Mat input){ //*********calculating the offset of the target from center of image******
		Mat processed = new Mat(input.height(),input.width(),CvType.CV_8UC3); //the final mat 8bit 3channel
		for(int i = 0; i<input.height(); i++){ //set all the pixels low
			for(int j = 0; j< input.width();j++){
				processed.put(i, j,0,0,0);
			}
		}
		double averageX = 0;
		double averageY = 0;
		for(int i = 0; i<finalTarget.total(); i++){ //add the target contour pixels in green
			double[] pixelData = finalTarget.get(i, 0);
			int pointY = (int) pixelData[1]; int pointX = (int) pixelData[0];
			averageY += pointY; averageX += pointX; 
			processed.put(pointY, pointX,0,255,0);
		}
		averageX = averageX/finalTarget.total(); averageY = averageY/finalTarget.total(); //find the average x and y
		System.out.println("AverageX: " + averageX + " AverageY: " + averageY);
		
		int height = input.height(); int width = input.width(); 
		int indexTL =-1 , indexTR =-1, indexBL =-1 , indexBR =-1; 
		double distanceTL = height+width; double distanceTR  = height+width; 
		double distanceBL = height+width; double distanceBR  = height+width;
		double TL, TR, BL, BR; 
		
		for(int i = 0; i<finalTarget.total(); i++){
			double[] pixelData = finalTarget.get(i, 0);
			int pointY = (int) pixelData[1]; int pointX = (int) pixelData[0];
			TL = Math.sqrt((pointX * pointX) + (pointY *pointY));
			TR = Math.sqrt((width-pointX)*(width-pointX) + (pointY)*(pointY));
			BL = Math.sqrt((pointX)*(pointX) + (height-pointY)*(height-pointY));
			BR = Math.sqrt((width-pointX)*(width-pointX) + (height-pointY)*(height-pointY));
			if(TL < distanceTL){distanceTL = TL; indexTL = i; System.out.println(distanceTL + " " +pointX+ " " + pointY);}
			if(TR < distanceTR){distanceTR = TR; indexTR = i;}
			if(BL < distanceBL){distanceBL = BL; indexBL = i;}
			if(BR < distanceBR){distanceBR = BR; indexBR = i;}
		}
		Point origin = new Point(0,0);
		Point brcorner = new Point(width,height);
		Point blcorner = new Point(0,height);
		Point tl = new Point(finalTarget.get(indexTL, 0));
		Point tr = new Point(finalTarget.get(indexTR, 0));
		Point bl = new Point(finalTarget.get(indexBL, 0));
		Point br = new Point(finalTarget.get(indexBR, 0));
		System.out.println("TL: " +tl.toString() + " "+ indexTL);
		System.out.println("TR: " +tr.toString()+ " "+ indexTR);
		System.out.println("BL: " +bl.toString()+ " "+ indexBL);
		System.out.println("BR: " +br.toString()+ " "+ indexBR);
		
		double slopeTLBR = 0; double slopeTRBL = 0;
		double interceptTLBR = 0; double interceptTRBL = 0;
		double intersectX, intersectY;
		
		slopeTLBR = (br.y - tl.y)/(br.x-tl.x); System.out.println("Slope TLBR: " + slopeTLBR);
		slopeTRBL = (tr.y - bl.y)/(tr.x-bl.x); System.out.println("Slope TRBL: " + slopeTRBL);
		interceptTLBR = tl.y-slopeTLBR*tl.x; System.out.println("Y Intercept TLBR: " + interceptTLBR);
		interceptTRBL = bl.y-slopeTRBL*bl.x; System.out.println("Y Intercept TRBL: " + interceptTRBL);
		
		intersectX = (interceptTRBL-interceptTLBR)/(slopeTLBR-slopeTRBL); System.out.println("X Intersect: " + intersectX);
		intersectY = intersectX*slopeTLBR + interceptTLBR; System.out.println("Y Intersect: " + intersectY);
		
		Point targetCenter = new Point(intersectX,intersectY); 
		Point imageCenter = new Point(input.width()/2,input.height()/2); 
		Point horizontalCorrection = new Point(input.width()/2,intersectY);
		targetCenterGlobal = targetCenter;
		horizontalCorrectionGlobal = horizontalCorrection;
		imageCenterGlobal = imageCenter;
		
		double xPixelCorrection = targetCenter.x-imageCenter.x; System.out.println("X Correction: " + xPixelCorrection);
		double yPixelCorrection = targetCenter.y-imageCenter.y; System.out.println("Y Correction: " + yPixelCorrection);
		
		double xDegreeCorrection = xPixelCorrection/input.width()*horizontalCameraAngle; System.out.println("X Correction (Degrees): " + xDegreeCorrection);
		double yDegreeCorrection = yPixelCorrection/input.height()*verticalCameraAngle; System.out.println("Y Correction (Degrees): " + yDegreeCorrection);
		
		xDegreeCorrectionGlobal = xDegreeCorrection;
		yDegreeCorrectionGlobal = yDegreeCorrection;
		
		//Imgproc.line(processed,origin,tl,white, 1);
		//Imgproc.line(processed,br,tl,white, 1); //cross
		//Imgproc.line(processed,tr,bl,white, 1); //cross
		Imgproc.line(processed,targetCenter,imageCenter,red, 1);
		Imgproc.line(processed,targetCenter,horizontalCorrection,white, 1);
		Imgproc.line(processed,imageCenter,horizontalCorrection,yellow, 1);
		Imgproc.circle(processed, targetCenter, 8, blue, 3);
		
		Imgproc.putText(processed, "X Degree Correction: " + xDegreeCorrection, blcorner, 1, 1, white);
		
		return processed; //returns the final mat
	}
	
	public static Mat superImpose(Mat input){ //*********Draw the lines on the originalImage*******
		Mat superImposed = new Mat(input.height(),input.width(),CvType.CV_8UC3);
		superImposed = originalImage;
		Point textLocation = new Point(5,input.height()-25);
		
		for(int i = 0; i<finalTarget.total(); i++){ //add the target contour pixels in green
			double[] pixelData = finalTarget.get(i, 0);
			int pointY = (int) pixelData[1]; int pointX = (int) pixelData[0];
			superImposed.put(pointY, pointX,255,255,255);
		}
		
		Imgproc.line(superImposed, targetCenterGlobal, imageCenterGlobal, red);
		Imgproc.line(superImposed, targetCenterGlobal, horizontalCorrectionGlobal, white);
		Imgproc.line(superImposed, imageCenterGlobal, horizontalCorrectionGlobal, yellow);
		Imgproc.circle(superImposed, targetCenterGlobal, 8, blue, 3);
		Imgproc.putText(superImposed, "X Degree Correction: " + xDegreeCorrectionGlobal, textLocation, 1, 1, white);
		Imgproc.putText(superImposed, "Y Degree Correction: " + yDegreeCorrectionGlobal, new Point(5,input.height()-10), 1, 1, white);
		return superImposed;
	}
	
	public static BufferedImage MatToBufferedImage(Mat mat) { //**********changes a mat into a buffered image********
	    // Fastest code
	    // output can be assigned either to a BufferedImage or to an Image

	    int type = BufferedImage.TYPE_BYTE_GRAY;
	    if ( mat.channels() > 1 ) {
	        type = BufferedImage.TYPE_3BYTE_BGR;
	    }
	    int bufferSize = mat.channels()*mat.cols()*mat.rows();
	    byte [] b = new byte[bufferSize];
	    mat.get(0,0,b); // get all the pixels
	    BufferedImage image = new BufferedImage(mat.cols(),mat.rows(), type);
	    final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
	    System.arraycopy(b, 0, targetPixels, 0, b.length);  
	    return image;
	}
	
	public ProcessTest(){   //yuck, a gui
		BufferedImage original = grabImage();
	    BufferedImage processed = MatToBufferedImage(colorFilter(BufferedImageToMat(grabImage())));
	    BufferedImage finalImage = MatToBufferedImage(contourDetection(colorFilter(BufferedImageToMat(grabImage()))));
	    BufferedImage errorImage = MatToBufferedImage(superImpose(errorCalculation(contourDetection(colorFilter(BufferedImageToMat(grabImage()))))));
	    ImageIcon icon = new ImageIcon(original);
	    ImageIcon output = new ImageIcon(processed);
	    ImageIcon finalOutput = new ImageIcon(finalImage);
	    ImageIcon errorOutput = new ImageIcon(errorImage);
	    JLabel originalImage = new JLabel();
	    JLabel processedImage = new JLabel();
	    JLabel finalOutputLabel = new JLabel();
	    JLabel errorOutputLabel = new JLabel();
	    originalImage.setIcon(icon);
	    processedImage.setIcon(output);
	    finalOutputLabel.setIcon(finalOutput);
	    errorOutputLabel.setIcon(errorOutput);
	    this.add(originalImage);
	    this.add(processedImage);
	    this.add(finalOutputLabel);
	    this.add(errorOutputLabel);
        
	}
	
	public static void main(String[] args) {
		
		JFrame frame = new JFrame();
	    frame.getContentPane().add(new ProcessTest());
	    
	    //contourDetection(colorFilter(BufferedImageToMat(grabImage())));
	    
	    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 540);
        frame.setVisible(true);
        
        ;
	}	
}
