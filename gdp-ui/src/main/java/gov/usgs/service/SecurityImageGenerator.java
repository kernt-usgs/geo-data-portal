package gov.usgs.service;

import gov.usgs.service.SecurityImageGenerator.Operations;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.*;
import javax.servlet.*;
import javax.servlet.http.*;



public class SecurityImageGenerator extends HttpServlet{

	private static final long serialVersionUID = 6407574646467990328L;

	public static enum Operations{getImage, validatecaptcha};

	@Override
	public void doPost (HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String command = request.getRequestURI().split("/")[3];
		Operations op = Operations.valueOf(command);
		switch(op) {
		case getImage:
			getImage(request, response);
			break;
		case validatecaptcha:
			validateCaptcha(request, response);
			break;
		default:	
			// TODO determine error handling
		}

	}

	public void validateCaptcha(HttpServletRequest request,  HttpServletResponse response) throws IOException{
		Boolean validCaptcha 	= Boolean.FALSE;
		String sessionCaptcha 	= (String) request.getSession().getAttribute("captcha");
		String userCaptcha		= request.getParameter("userCaptcha").toLowerCase(); 

		// TODO Determine better handling than this, perhaps use HTTP Status code? But there
		// doesn't seem to be appropriate code, and that sets up an defacto out-of-band protocol,
		// which may be a solution worse than the original problem.
		if (sessionCaptcha == null) {
                    throw new RuntimeException("session has expired");
                }

		if (sessionCaptcha.toLowerCase().equals(userCaptcha)) {
			validCaptcha = Boolean.TRUE;
		}

		StringBuffer sb = new StringBuffer();
		sb.append("{\"captcha\" : \"");
		sb.append(validCaptcha.toString());
		sb.append("\"}");
		response.setContentType("text/plain");  
		response.getWriter().write(sb.toString());
		response.getWriter().flush();
		response.getWriter().close();  
	}

	/**
	 * Creates and streams a random captcha image to the calling client.
	 * Also sets the random string that the image represents into the session object.
	 * 
	 * @param request
	 * @param response
	 */
	public void getImage(HttpServletRequest request, HttpServletResponse response) {
		String imageFormat = "jpg";
		response.setContentType("image/" + imageFormat);

		try {
			// you can pass in fontSize, width, height via the request   
			Color backgroundColor		= new Color(51,102,153);
			Color borderColor 			= Color.black;
			Color textColor 			= Color.white;
			Color circleColor 			= new Color(160,160,160);
			Font textFont 				= new Font("Arial", Font.PLAIN, paramInt(request, "fontSize", 24));
			int charsToPrint 			= paramInt(request, "charsToPrint", 6);
			int width 					= paramInt(request, "width", 150);
			int height 					= paramInt(request, "height", 80);
			int circlesToDraw			= paramInt(request, "circlesToDraw", 80);
			float horizMargin 			= 20.0f;
			float imageQuality 			= 0.95f; // max is 1.0 (this is for jpeg)
			double rotationRange 		= 0.999; // this is radians
			BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g 				= null;

			try {
				g 				= (Graphics2D) bufferedImage.getGraphics();

				g.setColor(backgroundColor);
				g.fillRect(0, 0, width, height);

				// lets make some noisy circles
				g.setColor(circleColor);
				for ( int i = 0; i < circlesToDraw; i++ ) {
					int circleRadius	= (int) (Math.random() * height / 2.0);
					int circleX 		= (int) (Math.random() * width - circleRadius);
					int circleY 		= (int) (Math.random() * height - circleRadius);
					g.drawOval(circleX, circleY, circleRadius * 2, circleRadius * 2);
				}
				g.setColor(textColor);
				g.setFont(textFont);

				FontMetrics fontMetrics		= g.getFontMetrics();
				int maxAdvance 				= fontMetrics.getMaxAdvance();
				int fontHeight 				= fontMetrics.getHeight();
				//String elegibleChars 		= "ABCDEFGHJKLMPQRSTUVWXYabcdefhjkmnpqrstuvwxy23456789";
				// All lowercase
				String elegibleChars 		= "ABCDEFGHJKLMPQRSTUVWXY23456789";
				char[] chars 				= elegibleChars.toCharArray();
				float spaceForLetters 		= -horizMargin * 2 + width;
				float spacePerChar 			= spaceForLetters / (charsToPrint - 1.0f);
				StringBuffer finalString 	= new StringBuffer();

				for ( int i = 0; i < charsToPrint; i++ ) {
					double randomValue 	= Math.random();
					int randomIndex 	= (int) Math.round(randomValue * (chars.length - 1));
					char characterToShow= chars[randomIndex];
					finalString.append(characterToShow);

					// this is a separate canvas used for the character so that
					// we can rotate it independently
					int charWidth 				= fontMetrics.charWidth(characterToShow);
					int charDim 				= Math.max(maxAdvance, fontHeight);
					int halfCharDim 			= charDim / 2;
					BufferedImage charImage	= new BufferedImage(charDim, charDim, BufferedImage.TYPE_INT_ARGB);
					double angle 				= (Math.random() - 0.5) * rotationRange;
					Graphics2D charGraphics 	= null;

					try {
						charGraphics = charImage.createGraphics();
						charGraphics.translate(halfCharDim, halfCharDim);	     
						charGraphics.transform(AffineTransform.getRotateInstance(angle));
						charGraphics.translate(-halfCharDim,-halfCharDim);
						charGraphics.setColor(textColor);
						charGraphics.setFont(textFont);

						int charX = (int) (0.5 * charDim - 0.5 * charWidth);
						charGraphics.drawString("" + characterToShow, charX, 
								(charDim - fontMetrics.getAscent()) 
								/ 2 + fontMetrics.getAscent());

						float x = horizMargin + spacePerChar * (i) - charDim / 2.0f;
						int y = (height - charDim) / 2;

						g.drawImage(charImage, (int) x, y, charDim, charDim, null, null);
					} finally {
						if (charGraphics != null) charGraphics.dispose();
					}

				}

				// let's stick the final string in the session
				request.getSession().setAttribute("captcha", finalString.toString());

				// let's do the border
				g.setColor(borderColor);
				g.drawRect(0, 0, width - 1, height - 1);

				//Write the image as a jpg
				Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName(imageFormat);
				if( iter.hasNext() ) {
					ImageWriter writer = null;
					try {
						writer = iter.next();
						ImageWriteParam iwp = writer.getDefaultWriteParam();

						if ( imageFormat.equalsIgnoreCase("jpg") || imageFormat.equalsIgnoreCase("jpeg") ) {
							iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
							iwp.setCompressionQuality(imageQuality);
						}

						writer.setOutput(ImageIO.createImageOutputStream(response.getOutputStream()));
						IIOImage imageIO = new IIOImage(bufferedImage, null, null);
						writer.write(null, imageIO, iwp);
					} finally {
						if (writer != null) writer.dispose();
					}
				} else {
					throw new RuntimeException("no encoder found for jsp");
				}

			} finally {
				if (g != null) g.dispose();
			}


		} catch (IOException ioe) {
			throw new RuntimeException("Unable to build image" , ioe);
		}
	}

	public static String paramString(HttpServletRequest request, String paramName, String defaultString) {
		return request.getParameter(paramName) != null ? request.getParameter(paramName) : defaultString;
	}

	public static int paramInt(HttpServletRequest request, String paramName, int defaultInt) {
		return request.getParameter(paramName) != null ? Integer.parseInt(request.getParameter(paramName)) : defaultInt;
	}
}
