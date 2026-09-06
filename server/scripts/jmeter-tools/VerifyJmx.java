import java.io.File;

import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testbeans.TestBeanHelper;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.PropertyIterator;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;

/**
 * 开发辅助工具：用 JMeter 自身的 SaveService 回读 .jmx，验证元件属性是否被正确解析。
 *
 * 第三方插件元件多为 TestBean，其 .jmx 属性键可能由父类常量重映射（如 bean 属性 numThreads
 * 实际存储为 ThreadGroup.num_threads），仅靠读 class 常量池无法确定。本工具把生成的 .jmx
 * 交给 JMeter 解析，再调用 TestBeanHelper.prepare() 让 bean 从属性回填，随后打印 bean 的
 * 生效值——键名写错时 bean 值会退回默认，从而可被断言捕获。
 *
 * 用法：
 *   java -cp ".tmp-jar;lib/*;lib/ext/*" VerifyJmx <jmeterHome> <plan.jmx>
 */
public class VerifyJmx {

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("usage: VerifyJmx <jmeterHome> <plan.jmx>");
      System.exit(2);
    }
    File home = new File(args[0]);
    JMeterUtils.loadJMeterProperties(new File(home, "bin/jmeter.properties").getAbsolutePath());
    JMeterUtils.setJMeterHome(home.getAbsolutePath());
    JMeterUtils.initLocale();
    SaveService.loadProperties();

    HashTree tree = SaveService.loadTree(new File(args[1]));
    walk(tree, 0);
  }

  private static void walk(HashTree tree, int depth) {
    for (Object key : tree.list()) {
      Object child = tree.get(key);
      if (key instanceof TestElement) {
        TestElement el = (TestElement) key;
        System.out.println(rep(depth) + "* " + shortName(el.getClass().getName()) + "  [" + el.getName() + "]");
        PropertyIterator it = el.propertyIterator();
        StringBuilder props = new StringBuilder();
        while (it.hasNext()) {
          JMeterProperty p = it.next();
          String n = p.getName();
          if (n.equalsIgnoreCase("name") || n.equalsIgnoreCase("guiclass") || n.equalsIgnoreCase("testclass")
              || n.equalsIgnoreCase("enabled") || n.equalsIgnoreCase("ThreadGroup.main_controller")) continue;
          String v;
          try {
            v = String.valueOf(p.getObjectValue());
          } catch (Throwable t) {
            v = "?";
          }
          if (v != null && v.length() > 120) v = v.substring(0, 120) + "…";
          props.append(n).append('=').append(v).append("  ");
        }
        System.out.println(rep(depth + 1) + props.toString().trim());
        // TestBean：prepare() 让 bean 从属性回填，随后读取生效值
        if (el instanceof TestBean) {
          try {
            TestBeanHelper.prepare(el);
            StringBuilder sb = new StringBuilder();
            for (java.beans.PropertyDescriptor pd : java.beans.Introspector.getBeanInfo(el.getClass(), Object.class).getPropertyDescriptors()) {
              if (pd.getReadMethod() == null || pd.getWriteMethod() == null) continue;
              String n = pd.getName();
              if (n.equals("samplerController") || n.equals("name") || n.equals("elementName") || n.equals("guiClass")
                  || n.equals("testClass") || n.equals("enabled") || n.equals("runningVersion")) continue;
              Object v;
              try {
                v = pd.getReadMethod().invoke(el);
              } catch (Throwable t) {
                v = "?";
              }
              String s = String.valueOf(v);
              if (s.length() > 90) s = s.substring(0, 90) + "…";
              sb.append(n).append('=').append(s).append("  ");
            }
            System.out.println(rep(depth + 1) + "bean> " + sb.toString().trim());
          } catch (Throwable t) {
            System.out.println(rep(depth + 1) + "bean> (失败：" + t + ")");
          }
        }
      } else {
        System.out.println(rep(depth) + "* " + shortName(key.getClass().getName()));
      }
      if (child instanceof HashTree) walk((HashTree) child, depth + 1);
    }
  }

  private static String rep(int n) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < n; i++) sb.append("  ");
    return sb.toString();
  }

  private static String shortName(String cn) {
    int i = cn.lastIndexOf('.');
    return i < 0 ? cn : cn.substring(i + 1);
  }
}
