package dynamicruntime.org;

import java.util.ArrayList;

public class CoreComponent {
    public void init() {
        var x = new ArrayList<String>();
        x.add("testString");
        System.out.println("Hello World " + x);
    }
}
