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
        String outerName = isInnerClass(cls);
        if (outerName != null) {
            for (MethodNode mn : cls.methods) {
                if (mn.name.equals("<init>")) {
                    // Found a constructor.
                    String methodInfo = mn.name + mn.desc + " in " + cls.name;
                    Type[] params = Type.getArgumentTypes(mn.desc);
                    if (params.length > 0 && params[0].getSort() == Type.OBJECT && params[0].getInternalName().equals(outerName)) {
                        if (mn.visibleParameterAnnotations != null) {
                            int numVisible = mn.visibleParameterAnnotations.length;
                            if (params.length == numVisible) {
                                LOGGER.info("Found extra RuntimeVisibleParameterAnnotations entry in " + methodInfo);
                                mn.visibleParameterAnnotations = Arrays.copyOfRange(mn.visibleParameterAnnotations, 1, numVisible);
                            } else if (params.length == numVisible - 1){
                                LOGGER.info("Number of RuntimeVisibleParameterAnnotations in " + methodInfo + " is already as we want");
                            } else {
                                LOGGER.warning("Unexpected number of RuntimeVisibleParameterAnnotations in " + methodInfo + ": " + numVisible);
                            }
                        }
                        if (mn.invisibleParameterAnnotations != null) {
                            int numInvisible = mn.invisibleParameterAnnotations.length;
                            if (params.length == numInvisible) {
                                LOGGER.info("Found extra RuntimeInvisibleParameterAnnotations entry in " + methodInfo);
                                mn.invisibleParameterAnnotations = Arrays.copyOfRange(mn.invisibleParameterAnnotations, 1, numInvisible);
                            } else if (params.length == numInvisible - 1){
                                LOGGER.info("Number of RuntimeInvisibleParameterAnnotations in " + methodInfo + " is already as we want");
                            } else {
                                LOGGER.warning("Unexpected number of RuntimeInvisibleParameterAnnotations in " + methodInfo + ": " + numInvisible);
                            }
                        }
                    } else {
                        LOGGER.warning("Unexpected lack of synthetic arg to the constructor: expected "
                                        + outerName + " on " + methodInfo);
                    }
                }
            }
        }
    }

    /**
     * Checks if the given class is an inner class, and thus the first
     * parameter to the constructor is synthetic.
     *
     * Returns the name of the outer class if it is inner (and can have the param),
     * and otherwise null.
     */
    private String isInnerClass(ClassNode cls) {
        InnerClassNode info = null;
        for (InnerClassNode node : cls.innerClasses) {
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
        if ((cls.access & (ACC_STATIC | ACC_INTERFACE)) != 0) {
            LOGGER.fine("  Not considering " + cls.name + " for extra parameter annotations as it has the wrong access " + cls.access);
            return null; // It's static or can't have a constructor
        }

        // http://hg.openjdk.java.net/jdk8/jdk8/langtools/file/1ff9d5118aae/src/share/classes/com/sun/tools/javac/jvm/ClassReader.java#l2011
        if (info.innerName == null) {
            LOGGER.fine("  Not considering " + cls.name + " for extra parameter annotations as it is annonymous");
            return null; // It's an anonymous class
        }

        LOGGER.fine("  Considering " + cls.name + " for extra parameter annotations as it is an inner class of " + info.outerName);

        return info.outerName;
    }
}
