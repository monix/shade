package shade.memcached.internals;

import net.spy.memcached.compat.log.AbstractLogger;
import net.spy.memcached.compat.log.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jLogger extends AbstractLogger {
    private Logger logger;

    public Slf4jLogger(String name) {
        super(name);
        logger = LoggerFactory.getLogger(name);
    }


    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    @Override public void log(Level level, Object message, Throwable e) {
        switch (level) {
            case DEBUG:
                logger.debug("{}", message, e);
                break;
            case INFO:
                logger.info("{}", message, e);
                break;
            case WARN:
                logger.warn("{}", message, e);
                break;
            case ERROR:
                logger.error("{}", message, e);
                break;
            case FATAL:
                logger.error("{}", message, e);
                break;
            default:
                logger.error("Unhandled log level: {}", level);
                logger.error("{}", message, e);
        }
    }

}
