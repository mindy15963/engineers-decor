/*
 * @file ModTesrs.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2018 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Yet unstructured initial experiments with TESRs.
 * May be structured after I know what I am doing there.
 */
package wile.engineersdecor.detail;

import wile.engineersdecor.ModEngineersDecor;
import wile.engineersdecor.blocks.EdCraftingTable;
import wile.engineersdecor.blocks.EdLabeledCrate;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.mojang.blaze3d.platform.GlStateManager;
import wile.engineersdecor.blocks.EdLabeledCrate.LabeledCrateBlock;

public class ModTesrs
{
  //--------------------------------------------------------------------------------------------------------------------
  // Crafting table
  //--------------------------------------------------------------------------------------------------------------------

  @OnlyIn(Dist.CLIENT)
  public static class TesrDecorCraftingTable extends TileEntityRenderer<EdCraftingTable.CraftingTableTileEntity>
  {
    private static int tesr_error_counter = 4;
    private static double scaler = 0.10;
    private static double gap = 0.19;
    private static double yrotations[] = {0, 90, 180, 270}; // [hdirection] S-W-N-E
    private static double offsets[][][] = { // [hdirection][slotindex][xz]
      { {-1,-1},{+0,-1},{+1,-1}, {-1,+0},{+0,+0},{+1,+0}, {-1,+1},{+0,+1},{+1,+1} }, // S
      { {+1,-1},{+1,+0},{+1,+1}, {+0,-1},{+0,+0},{+0,+1}, {-1,-1},{-1,+0},{-1,+1} }, // W
      { {+1,+1},{+0,+1},{-1,+1}, {+1,+0},{+0,+0},{-1,+0}, {+1,-1},{+0,-1},{-1,-1} }, // N
      { {-1,+1},{-1,+0},{-1,-1}, {+0,+1},{+0,+0},{+0,-1}, {+1,+1},{+1,+0},{+1,-1} }, // E
    };

    @Override
    @SuppressWarnings("deprecation")
    public void render(final EdCraftingTable.CraftingTableTileEntity te, double x, double y, double z, float partialTicks, int destroyStage)
    {
      if(tesr_error_counter<=0) return;
      try {
        int di = MathHelper.clamp(te.getWorld().getBlockState(te.getPos()).get(EdCraftingTable.CraftingTableBlock.HORIZONTAL_FACING).getHorizontalIndex(), 0, 3);
        long posrnd = te.getPos().toLong();
        posrnd = (posrnd>>16)^(posrnd<<1);
        for(int i=0; i<9; ++i) {
          final ItemStack stack = te.getStackInSlot(i);
          if(stack.isEmpty()) continue;
          double prnd = ((double)(((Integer.rotateRight(stack.getItem().hashCode()^(int)posrnd,(stack.getCount()+i)&31)))&1023))/1024.0;
          double rndo = gap * ((prnd*0.1)-0.05);
          double ox = gap * offsets[di][i][0], oz = gap * offsets[di][i][1];
          double oy = 0.5;
          double ry = ((yrotations[di]+180) + ((prnd*60)-30)) % 360;
          if(stack.isEmpty()) return;
          GlStateManager.pushMatrix();
          GlStateManager.disableLighting();
          RenderHelper.enableStandardItemLighting();
          GlStateManager.translated(x+0.5+ox, y+0.5+oy, z+0.5+oz);
          GlStateManager.rotated(90, 1, 0, 0);
          GlStateManager.rotated(ry, 0, 0, 1);
          GlStateManager.translated(rndo, rndo, 0);
          GlStateManager.scaled(scaler, scaler, scaler);
          Minecraft.getInstance().getItemRenderer().renderItem(stack, net.minecraft.client.renderer.model.ItemCameraTransforms.TransformType.FIXED);
          RenderHelper.disableStandardItemLighting();
          GlStateManager.enableLighting();
          GlStateManager.popMatrix();
        }
      } catch(Throwable e) {
        if(--tesr_error_counter<=0) {
          ModEngineersDecor.logger().error("TESR was disabled because broken, exception was: " + e.getMessage());
          ModEngineersDecor.logger().error(e.getStackTrace());
        }
      }
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Labeled Crate
  //--------------------------------------------------------------------------------------------------------------------

  @OnlyIn(Dist.CLIENT)
  public static class TesrDecorLabeledCrate extends TileEntityRenderer<EdLabeledCrate.LabeledCrateTileEntity>
  {
    private static int tesr_error_counter = 4;
    private static double scaler = 0.35;
    double tr[][]= { // [hdirection=S-W-N-E][param]
      {  +8.0/32, -8.0/32, +15.5/32, 180.0 }, // N
      { -15.5/32, -8.0/32,  +8.0/32,  90.0 }, // E
      {  -8.0/32, -8.0/32, -15.5/32,   0.0 }, // S param=tx,ty,tz,ry
      { +15.5/32, -8.0/32,  -8.0/32, 270.0 }, // W
    };

    @Override
    @SuppressWarnings("deprecation")
    public void render(final EdLabeledCrate.LabeledCrateTileEntity te, double x, double y, double z, float partialTicks, int destroyStage)
    {
      if(tesr_error_counter<=0) return;
      try {
        final ItemStack stack = te.getItemFrameStack();
        if(stack.isEmpty()) return;
        final int di = MathHelper.clamp(te.getWorld().getBlockState(te.getPos()).get(LabeledCrateBlock.HORIZONTAL_FACING).getHorizontalIndex(), 0, 3);
        double ox = tr[di][0], oy = tr[di][1], oz = tr[di][2], ry = tr[di][3];
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.translated(x+0.5+ox, y+0.5+oy, z+0.5+oz);
        GlStateManager.rotated(ry, 0, 1, 0);
        GlStateManager.scaled(scaler, scaler, scaler);
        Minecraft.getInstance().getItemRenderer().renderItem(stack, net.minecraft.client.renderer.model.ItemCameraTransforms.TransformType.FIXED);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
      } catch(Throwable e) {
        if(--tesr_error_counter<=0) {
          ModEngineersDecor.logger().error("TESR was disabled (because broken), exception was: " + e.getMessage());
        }
      }
    }
  }
}
