package tadsuite.mvc;

import java.util.Timer;
import java.util.TimerTask;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener("Tadsuite Application Startup Listener")
public final class BootstrapListener implements ServletContextListener {

	private Timer timer = null;
	private ServletContext servlet;
	
	
	public void contextInitialized(ServletContextEvent event)  {
		servlet=event.getServletContext();
		Application.init(servlet);
		
		timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				Application.reloadConfigurationFile();
			}
		}, 30*1000, 30*1000);
    }

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		if (timer!=null) {
			timer.cancel();
			timer=null;
		}
	}
}
