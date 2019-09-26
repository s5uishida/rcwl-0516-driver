package io.github.s5uishida.iot.device.rcwl0516.driver;

import java.util.Date;

/*
 * @author s5uishida
 *
 */
public interface IRCWL0516Handler {
	void handle(String pinName, boolean detect, Date date);
}
