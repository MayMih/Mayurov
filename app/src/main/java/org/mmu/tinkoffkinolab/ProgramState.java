package org.mmu.tinkoffkinolab;

public class ProgramState
{
    private static boolean isFirstLaunch = true;
    
    
    public static boolean getIsFirstLaunch()
    {
        return isFirstLaunch;
    }
    
    public static void setIsFirstLaunch(boolean value)
    {
        ProgramState.isFirstLaunch = value;
    }
}
