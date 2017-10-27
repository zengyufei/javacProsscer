import lombok.extern.jbosslog.JBossLog;
@lombok.extern.jbosslog.JBossLog class LoggerJBossLog {
  private static final org.jboss.logging.Logger log = org.jboss.logging.Logger.getLogger(LoggerJBossLog.class);
  <clinit>() {
  }
  LoggerJBossLog() {
    super();
  }
}
@JBossLog class LoggerJBossLogWithImport {
  private static final org.jboss.logging.Logger log = org.jboss.logging.Logger.getLogger(LoggerJBossLogWithImport.class);
  <clinit>() {
  }
  LoggerJBossLogWithImport() {
    super();
  }
}
class LoggerJBossLogOuter {
  static @lombok.extern.jbosslog.JBossLog class Inner {
    private static final org.jboss.logging.Logger log = org.jboss.logging.Logger.getLogger(Inner.class);
    <clinit>() {
    }
    Inner() {
      super();
    }
  }
  LoggerJBossLogOuter() {
    super();
  }
}
@JBossLog(topic = "DifferentLogger") class LoggerJBossLogWithDifferentLoggerName {
  private static final org.jboss.logging.Logger log = org.jboss.logging.Logger.getLogger("DifferentLogger");
  <clinit>() {
  }
  LoggerJBossLogWithDifferentLoggerName() {
    super();
  }
}