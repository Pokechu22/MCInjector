package de.oceanlabs.mcp.mcinjector.adaptors;

import static org.objectweb.asm.Opcodes.*;

import java.util.Arrays;
import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

import de.oceanlabs.mcp.mcinjector.MCInjectorImpl;

public class ParameterAnnotationFixer extends ClassVisitor {

    private static final Logger LOGGER = Logger.getLogger("MCInjector");

    public ParameterAnnotationFixer(ClassVisitor cn, MCInjectorImpl mci)
    {
        super(Opcodes.ASM5, cn);
    }

    @Override
    public void visitEnd()
    {
        super.visitEnd();

        ClassNode cls = MCInjectorImpl.getClassNode(cv);
        Type[] syntheticParams = canHaveShiftedParams(cls);
        if (syntheticParams != null) {
            for (MethodNode mn : cls.methods) {
                if (mn.name.equals("<init>")) {
                    // Found a constructor.
                    String methodInfo = mn.name + mn.desc + " in " + cls.name;
                    Type[] params = Type.getArgumentTypes(mn.desc);
                    if (startsMatch(params, syntheticParams)) {
                        if (mn.visibleParameterAnnotations != null) {
                            int numVisible = mn.visibleParameterAnnotations.length;
                            if (params.length == numVisible) {
                                LOGGER.info("Found extra RuntimeVisibleParameterAnnotations entries in " + methodInfo + ": removing " + Arrays.toString(syntheticParams));
                                mn.visibleParameterAnnotations = Arrays.copyOfRange(mn.visibleParameterAnnotations, syntheticParams.length, numVisible);
                            } else if (params.length == numVisible - syntheticParams.length) {
                                LOGGER.info("Number of RuntimeVisibleParameterAnnotations in " + methodInfo + " is already as we want");
                            } else {
                                LOGGER.warning("Unexpected number of RuntimeVisibleParameterAnnotations in " + methodInfo + ": " + numVisible);
                            }
                        } else {
                            LOGGER.finer("    " + methodInfo + " does not have a RuntimeVisibleParameterAnnotations attribute");
                        }
                        if (mn.invisibleParameterAnnotations != null) {
                            int numInvisible = mn.invisibleParameterAnnotations.length;
                            if (params.length == numInvisible) {
                                LOGGER.info("Found extra RuntimeInvisibleParameterAnnotations entries in " + methodInfo + ": removing " + Arrays.toString(syntheticParams));
                                mn.invisibleParameterAnnotations = Arrays.copyOfRange(mn.invisibleParameterAnnotations, syntheticParams.length, numInvisible);
                            } else if (params.length == numInvisible - syntheticParams.length) {
                                LOGGER.info("Number of RuntimeInvisibleParameterAnnotations in " + methodInfo + " is already as we want");
                            } else {
                                LOGGER.warning("Unexpected number of RuntimeInvisibleParameterAnnotations in " + methodInfo + ": " + numInvisible);
                            }
                        } else {
                            LOGGER.finer("    " + methodInfo + " does not have a RuntimeInvisibleParameterAnnotations attribute");
                        }
                    } else {
                        LOGGER.warning("Unexpected lack of synthetic args to the constructor: expected "
                                        + Arrays.toString(syntheticParams) + " at the start of " + methodInfo);
                    }
                }
            }
        }
    }

    /**
     * Checks if the given class might have shifted parameter annotations in the
     * constructor. There are two cases where this might happen:
     * <ol>
     * <li>If the given class is an inner class, the first parameter to the
     * constructor is synthetic.</li>
     * <li>if the given class is an enum, the first parameter is the enum
     * constant name and the second parameter is its ordinal.</li>
     * </ol>
     *
     * @return The types of the synthetic parameters if the class might have
     *         shifted parameters, otherwise null.
     */
    private Type[] canHaveShiftedParams(ClassNode cls) {
        // Check for enum
        // http://hg.openjdk.java.net/jdk8/jdk8/langtools/file/1ff9d5118aae/src/share/classes/com/sun/tools/javac/comp/Lower.java#l2866
        if ((cls.access & ACC_ENUM) != 0) {
            LOGGER.fine("  Considering " + cls.name + " for extra parameter annotations as it is an enum");
            return new Type[] { Type.getObjectType("java/lang/String"), Type.INT_TYPE };
        }

        // Check for inner class
        InnerClassNode info = null;
        for (InnerClassNode node : cls.innerClasses) { // note: cls.innerClasses is never null
            if (node.name.equals(cls.name)) {
                info = node;
                break;
            }
        }
        // http://hg.openjdk.java.net/jdk8/jdk8/langtools/file/1ff9d5118aae/src/share/classes/com/sun/tools/javac/code/Symbol.java#l398
        if (info == null) {
            LOGGER.fine("  Not considering " + cls.name + " for extra parameter annotations as it is not an inner class");
            return null; // It's not an inner class
        }
        if ((info.access & (ACC_STATIC | ACC_INTERFACE)) != 0) {
            LOGGER.fine("  Not considering " + cls.name + " for extra parameter annotations as it has the wrong access " + cls.access);
            return null; // It's static or can't have a constructor
        }

        // http://hg.openjdk.java.net/jdk8/jdk8/langtools/file/1ff9d5118aae/src/share/classes/com/sun/tools/javac/jvm/ClassReader.java#l2011
        if (info.innerName == null) {
            LOGGER.fine("  Not considering " + cls.name + " for extra parameter annotations as it is annonymous");
            return null; // It's an anonymous class
        }

        LOGGER.fine("  Considering " + cls.name + " for extra parameter annotations as it is an inner class of " + info.outerName);

        return new Type[] { Type.getObjectType(info.outerName) };
    }

    private boolean startsMatch(Type[] values, Type[] prefix) {
        if (values.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (!values[i].equals(prefix[i])) {
                return false;
            }
        }
        return true;
    }
}
