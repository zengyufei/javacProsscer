//CONF: lombok.var.flagUsage = ALLOW
import lombok.experimental.var;

public class VarInFor {
	public void enhancedFor() {
		int[] list = new int[] {1, 2};
		for (var shouldBeInt : list) {
			System.out.println(shouldBeInt);
			var shouldBeInt2 = shouldBeInt;
		}
	}
}