package xiaofei.library.zlang;

/**
 * Created by Xiaofei on 2017/9/30.
 */
public interface JavaFunction {
    boolean isVarArgs();

    int getParameterNumber();

    String getFunctionName();

    Object call(Object[] input);
}
