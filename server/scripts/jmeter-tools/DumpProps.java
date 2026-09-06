import java.lang.reflect.Method;

import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.PropertyIterator;
import org.apache.jorphan.reflect.ClassFinder;

/**
 * 开发辅助工具：打印 JMeter 元件（含第三方插件）的 @TestElementProps 权威 schema。
 *
 * 用途：平台生成 .jmx 时必须使用元件真实的属性键名与默认值，而这些键名分散在插件 class 的
 * 注解里、无法从文档可靠获得，故用反射直接读取，避免猜错导致 JMeter 打不开或参数失效。
 *
 * 编译/运行（在 server/jmeter 目录下）：
 *   javac -cp "lib/*;lib/ext/*" -d .tmp-jar tools/DumpProps.java
 *   java  -cp ".tmp-jar;lib/*;lib/ext/*" DumpProps kg.apc.jmeter.threads.SteppingThreadGroup ...
 */
public class DumpProps {

  public static void main(String[] args) throws Exception {
    for (String cn : args) {
      System.out.println("=== " + cn + " ===");
      Class<?> c;
      try {
        c = Class.forName(cn);
      } catch (Throwable t) {
        System.out.println("  !! 无法加载：" + t.getMessage());
        continue;
      }
      // 1) TestBean 元件的属性键 == JavaBean 属性名（JMeter 的 TestBeanHelper 按此读写），
      //    class 常量池里不会有字面量，故必须用 Introspector 枚举
      printBeanInfo(c);
      // 2) 实例化后遍历其属性集合（非 TestBean 元件走这条路）
      printProperties(c);
      System.out.println();
    }
  }

  private static void printBeanInfo(Class<?> c) {
    try {
      java.beans.BeanInfo bi = java.beans.Introspector.getBeanInfo(c, Object.class);
      StringBuilder sb = new StringBuilder();
      for (java.beans.PropertyDescriptor pd : bi.getPropertyDescriptors()) {
        if (pd.getWriteMethod() == null) continue; // 只读属性不参与 .jmx 存储
        sb.append(pd.getName()).append(':').append(pd.getPropertyType().getSimpleName()).append("  ");
      }
      System.out.println("  [bean props] " + sb.toString().trim());
    } catch (Throwable t) {
      System.out.println("  (BeanInfo 读取失败：" + t + ")");
    }
  }

  private static void printAnnotation(Class<?> c) {
    for (java.lang.annotation.Annotation a : c.getDeclaredAnnotations()) {
      String s = a.toString();
      if (!s.contains("TestElementProps") && !s.contains("BeanInfo")) continue;
      System.out.println("  [annotation] " + s.replaceAll("\\s+", " ").trim());
      try {
        Method propsM = a.annotationType().getMethod("props");
        Object[] arr = (Object[]) propsM.invoke(a);
        for (Object p : arr) {
          Class<?> pt = p.getClass();
          System.out.printf("      key=%-42s default=%-10s name=%s%n",
              pt.getMethod("key").invoke(p),
              String.valueOf(pt.getMethod("defaultValue").invoke(p)),
              pt.getMethod("name").invoke(p));
        }
      } catch (Throwable t) {
        System.out.println("      (读取注解明细失败：" + t + ")");
      }
    }
  }

  private static void printProperties(Class<?> c) {
    try {
      Object inst = c.getDeclaredConstructor().newInstance();
      if (!(inst instanceof org.apache.jmeter.testelement.AbstractTestElement)) {
        System.out.println("  (非 AbstractTestElement，跳过属性遍历)");
        return;
      }
      org.apache.jmeter.testelement.AbstractTestElement el = (org.apache.jmeter.testelement.AbstractTestElement) inst;
      PropertyIterator it = el.propertyIterator();
      int n = 0;
      while (it.hasNext()) {
        Object p = it.next();
        String name = (String) p.getClass().getMethod("getName").invoke(p);
        String val;
        try {
          val = String.valueOf(((org.apache.jmeter.testelement.property.JMeterProperty) p).getObjectValue());
        } catch (Throwable t) {
          val = "?";
        }
        if (name.startsWith("guiclass") || name.startsWith("testclass") || name.startsWith("name") || name.startsWith("elementName")) continue;
        System.out.printf("  prop  %-46s = %s%n", name, val);
        n++;
      }
      if (n == 0) System.out.println("  (实例属性集合为空：键名见上方注解或 GUI)");
    } catch (Throwable t) {
      System.out.println("  (实例化失败：" + t + ")");
    }
  }
}
