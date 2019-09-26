package io.github.s5uishida.iot.device.rcwl0516.driver;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiGpioProvider;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.RaspiPinNumberingScheme;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

/*
 * Refer to https://www.epitran.it/ebayDrive/datasheet/19.pdf, https://github.com/jxmot/ESP8266-RCWL0516
 *
 * @author s5uishida
 *
 */
public class RCWL0516Driver {
	private static final Logger LOG = LoggerFactory.getLogger(RCWL0516Driver.class);

	private final Pin gpioPin;
	private final GpioController gpio;
	private final IRCWL0516Handler rcwl0516Handler;
	private final String logPrefix;

	private GpioPinDigitalInput diPin;

	private static final ConcurrentHashMap<String, RCWL0516Driver> map = new ConcurrentHashMap<String, RCWL0516Driver>();

	private final AtomicInteger useCount = new AtomicInteger(0);

	private RCWL0516GpioPinListenerDigital rcwl0516Listener;

	synchronized public static RCWL0516Driver getInstance() {
		return getInstance(RaspiPin.GPIO_10, null);
	}

	synchronized public static RCWL0516Driver getInstance(Pin gpioPin) {
		return getInstance(gpioPin, null);
	}

	synchronized public static RCWL0516Driver getInstance(Pin gpioPin, IRCWL0516Handler rcwl0516Handler) {
		String key = getName(Objects.requireNonNull(gpioPin));
		RCWL0516Driver rcwl0516 = map.get(key);
		if (rcwl0516 == null) {
			rcwl0516 = new RCWL0516Driver(gpioPin, rcwl0516Handler);
			map.put(key, rcwl0516);
		}
		return rcwl0516;
	}

	private RCWL0516Driver(Pin gpioPin, IRCWL0516Handler rcwl0516Handler) {
		if (gpioPin.equals(RaspiPin.GPIO_18) || gpioPin.equals(RaspiPin.GPIO_19) ||
				gpioPin.equals(RaspiPin.GPIO_12) || gpioPin.equals(RaspiPin.GPIO_13)) {
			this.gpioPin = gpioPin;
		} else {
			throw new IllegalArgumentException("The set " + getName(gpioPin) + " is not " +
					getName(RaspiPin.GPIO_18) + ", " +
					getName(RaspiPin.GPIO_19) + ", " +
					getName(RaspiPin.GPIO_12) + " or " +
					getName(RaspiPin.GPIO_13) + ".");
		}
		logPrefix = "[" + getName() + "] ";
		GpioFactory.setDefaultProvider(new RaspiGpioProvider(RaspiPinNumberingScheme.BROADCOM_PIN_NUMBERING));
		gpio = GpioFactory.getInstance();
		this.rcwl0516Handler = rcwl0516Handler;
	}

	synchronized public void open() {
		try {
			LOG.debug(logPrefix + "before - useCount:{}", useCount.get());
			if (useCount.compareAndSet(0, 1)) {
				diPin = gpio.provisionDigitalInputPin(gpioPin, PinPullResistance.PULL_DOWN);
				diPin.setShutdownOptions(true);
				rcwl0516Listener = new RCWL0516GpioPinListenerDigital(this);
				diPin.addListener(rcwl0516Listener);
				LOG.info(logPrefix + "opened");
			}
		} finally {
			LOG.debug(logPrefix + "after - useCount:{}", useCount.get());
		}
	}

	synchronized public void close() {
		try {
			LOG.debug(logPrefix + "before - useCount:{}", useCount.get());
			if (useCount.compareAndSet(1, 0)) {
				diPin.removeAllListeners();
				gpio.unprovisionPin(diPin);
//				gpio.shutdown();
				LOG.info(logPrefix + "closed");
			}
		} finally {
			LOG.debug(logPrefix + "after - useCount:{}", useCount.get());
		}
	}

	public static String getName(Pin gpioPin) {
		return gpioPin.getName().replaceAll("\\s", "_");
	}

	public String getName() {
		return gpioPin.getName().replaceAll("\\s", "_");
	}

	public String getLogPrefix() {
		return logPrefix;
	}

	/******************************************************************************************************************
	 * Sample main
	 ******************************************************************************************************************/
	public static void main(String[] args) {
		RCWL0516Driver rcwl0516 = RCWL0516Driver.getInstance(RaspiPin.GPIO_18, new MyRCWL0516Handler());
		rcwl0516.open();

//		if (rcwl0516 != null) {
//			rcwl0516.close();
//		}
	}

	class RCWL0516GpioPinListenerDigital implements GpioPinListenerDigital {
		private final Logger LOG = LoggerFactory.getLogger(RCWL0516GpioPinListenerDigital.class);

		private final RCWL0516Driver rcwl0516;

		public RCWL0516GpioPinListenerDigital(RCWL0516Driver rcwl0516) {
			this.rcwl0516 = rcwl0516;
		}

		@Override
		public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
			LOG.trace(rcwl0516.getLogPrefix() + "{} -> {}", event.getPin(), event.getState());

			Date date = new Date();
			if (event.getState() == PinState.HIGH) {
				rcwl0516.rcwl0516Handler.handle(rcwl0516.getName(), true, date);
			} else if (event.getState() == PinState.LOW) {
				rcwl0516.rcwl0516Handler.handle(rcwl0516.getName(), false, date);
			}
		}
	}
}

/******************************************************************************************************************
 * Sample implementation of IRCWL0516Handler interface
 ******************************************************************************************************************/
class MyRCWL0516Handler implements IRCWL0516Handler {
	private static final Logger LOG = LoggerFactory.getLogger(MyRCWL0516Handler.class);

	private static final String dateFormat = "yyyy-MM-dd HH:mm:ss.SSS";
	private static final SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);

	@Override
	public void handle(String pinName, boolean detect, Date date) {
		LOG.info("[{}] {} {}", pinName, detect, sdf.format(date));
	}
}
