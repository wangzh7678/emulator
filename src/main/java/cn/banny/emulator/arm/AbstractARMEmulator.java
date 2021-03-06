package cn.banny.emulator.arm;

import cn.banny.emulator.AbstractEmulator;
import cn.banny.emulator.memory.Memory;
import cn.banny.emulator.memory.SvcMemory;
import cn.banny.emulator.SyscallHandler;
import cn.banny.emulator.debugger.Debugger;
import cn.banny.emulator.linux.ARMSyscallHandler;
import cn.banny.emulator.linux.Module;
import cn.banny.emulator.linux.AndroidElfLoader;
import cn.banny.emulator.linux.android.ArmLD;
import cn.banny.emulator.linux.file.FileIO;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.ArmConst;
import unicorn.EventMemHook;
import unicorn.Unicorn;
import unicorn.UnicornConst;

import java.io.File;
import java.io.IOException;

public abstract class AbstractARMEmulator extends AbstractEmulator {

    private static final Log log = LogFactory.getLog(AbstractARMEmulator.class);

    protected final Memory memory;
    private final ARMSyscallHandler syscallHandler;
    private final SvcMemory svcMemory;

    public AbstractARMEmulator(String processName) {
        super(UnicornConst.UC_ARCH_ARM, UnicornConst.UC_MODE_ARM, processName);

        Cpsr.getArm(unicorn).switchUserMode();

        unicorn.hook_add(new EventMemHook() {
            @Override
            public boolean hook(Unicorn u, long address, int size, long value, Object user) {
                log.debug("memory failed: address=0x" + Long.toHexString(address) + ", size=" + size + ", value=0x" + Long.toHexString(value) + ", user=" + user);
                return false;
            }
        }, UnicornConst.UC_HOOK_MEM_READ_UNMAPPED | UnicornConst.UC_HOOK_MEM_WRITE_UNMAPPED | UnicornConst.UC_HOOK_MEM_FETCH_UNMAPPED, null);

        this.svcMemory = new ARMSvcMemory(unicorn, 0xfffe0000L, 0x10000, this);
        this.syscallHandler = new ARMSyscallHandler(svcMemory);

        enableVFP();
        this.memory = new AndroidElfLoader(unicorn, this, syscallHandler);
        this.memory.addHookListener(new ArmLD(unicorn, svcMemory));

        unicorn.hook_add(syscallHandler, this);
    }

    private void enableVFP() {
        int value = ((Number) unicorn.reg_read(ArmConst.UC_ARM_REG_C1_C0_2)).intValue();
        value |= (0xf << 20);
        unicorn.reg_write(ArmConst.UC_ARM_REG_C1_C0_2, value);
        unicorn.reg_write(ArmConst.UC_ARM_REG_FPEXC, 0x40000000);
    }

    @Override
    protected Debugger createDebugger() {
        return new SimpleARMDebugger();
    }

    @Override
    protected void closeInternal() {
        for (FileIO io : syscallHandler.fdMap.values()) {
            io.close();
        }
    }

    @Override
    public Module loadLibrary(File libraryFile) throws IOException {
        return memory.load(libraryFile);
    }

    @Override
    public Module loadLibrary(File libraryFile, boolean forceCallInit) throws IOException {
        return memory.load(libraryFile, forceCallInit);
    }

    public SvcMemory getSvcMemory() {
        return svcMemory;
    }

    @Override
    public Memory getMemory() {
        return memory;
    }

    @Override
    public SyscallHandler getSyscallHandler() {
        return syscallHandler;
    }
}
