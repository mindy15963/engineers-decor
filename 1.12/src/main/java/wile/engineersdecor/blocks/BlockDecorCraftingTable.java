/*
 * @file BlockDecorCraftingTable.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2019 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Mod crafting table, different style, UI and fetature set
 * than vanilla crafting table.
 */
package wile.engineersdecor.blocks;

import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import org.lwjgl.Sys;
import org.lwjgl.input.Mouse;
import wile.engineersdecor.ModEngineersDecor;
import wile.engineersdecor.detail.ModAuxiliaries;
import wile.engineersdecor.detail.Networking;
import net.minecraft.world.World;
import net.minecraft.world.Explosion;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiButtonImage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import com.google.common.collect.ImmutableList;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.sound.midi.SysexMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class BlockDecorCraftingTable extends BlockDecorDirected
{
  public static boolean with_assist = true;
  public static boolean with_assist_direct_history_refab = false;
  public static boolean with_assist_quickmove_buttons = false;
  public static boolean with_crafting_slot_mouse_scrolling = true;

  public static final void on_config(boolean without_crafting_assist, boolean with_assist_immediate_history_refab,
                                     boolean with_quickmove_buttons, boolean without_crafting_slot_mouse_scrolling)
  {
    with_assist = !without_crafting_assist;
    with_assist_direct_history_refab = with_assist_immediate_history_refab;
    with_assist_quickmove_buttons = with_quickmove_buttons;
    with_crafting_slot_mouse_scrolling = !without_crafting_slot_mouse_scrolling;
    CraftingHistory.max_history_size(32);
  }

  public BlockDecorCraftingTable(@Nonnull String registryName, long config, @Nullable Material material, float hardness, float resistance, @Nullable SoundType sound, @Nonnull AxisAlignedBB unrotatedAABB)
  {
    super(registryName, config, material, hardness, resistance, sound, unrotatedAABB);
    setLightOpacity(0);
  }

  @Override
  public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand)
  { return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite()); }

  @Override
  public boolean hasTileEntity(IBlockState state)
  { return true; }

  @Override
  @Nullable
  public TileEntity createTileEntity(World world, IBlockState state)
  { return new BlockDecorCraftingTable.BTileEntity(); }

  @Override
  public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ)
  {
    if(world.isRemote) return true;
    player.openGui(ModEngineersDecor.instance, ModEngineersDecor.GuiHandler.GUIID_CRAFTING_TABLE, world, pos.getX(), pos.getY(), pos.getZ());
    return true;
  }

  @Override
  public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack)
  {
    if(world.isRemote) return;
    if((!stack.hasTagCompound()) || (!stack.getTagCompound().hasKey("inventory"))) return;
    NBTTagCompound inventory_nbt = stack.getTagCompound().getCompoundTag("inventory");
    if(inventory_nbt.isEmpty()) return;
    final TileEntity te = world.getTileEntity(pos);
    if(!(te instanceof BTileEntity)) return;
    ((BTileEntity)te).readnbt(inventory_nbt);
    ((BTileEntity)te).markDirty();
  }

  private ItemStack itemize_with_inventory(World world, BlockPos pos)
  {
    TileEntity te = world.getTileEntity(pos);
    if(!(te instanceof BTileEntity)) return ItemStack.EMPTY;
    ItemStack stack = new ItemStack(this, 1);
    NBTTagCompound inventory_nbt = new NBTTagCompound();
    ItemStackHelper.saveAllItems(inventory_nbt, ((BTileEntity)te).stacks, false);
    if(!inventory_nbt.isEmpty()) {
      NBTTagCompound nbt = new NBTTagCompound();
      nbt.setTag("inventory", inventory_nbt);
      stack.setTagCompound(nbt);
    }
    return stack;
  }

  @Override
  public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest)
  {
    if(world.isRemote) return true;
    final ItemStack stack = itemize_with_inventory(world, pos);
    if(stack != ItemStack.EMPTY) {
      world.spawnEntity(new EntityItem(world, pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5, stack));
      world.setBlockToAir(pos);
      world.removeTileEntity(pos);
      return false;
    } else {
      return super.removedByPlayer(state, world, pos, player, willHarvest);
    }
  }

  @Override
  public void onBlockExploded(World world, BlockPos pos, Explosion explosion)
  {
    if(world.isRemote) return;
    TileEntity te = world.getTileEntity(pos);
    if(!(te instanceof BTileEntity)) return;
    for(ItemStack stack: ((BTileEntity)te).stacks) {
      if(!stack.isEmpty()) world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), stack));
    }
    ((BTileEntity)te).reset();
    super.onBlockExploded(world, pos, explosion);
  }

  //--------------------------------------------------------------------------------------------------------------------
  // ModEngineersDecor.GuiHandler connectors
  //--------------------------------------------------------------------------------------------------------------------

  public static Object getServerGuiElement(final EntityPlayer player, final World world, final BlockPos pos, final TileEntity te)
  { return (te instanceof BTileEntity) ? (new BContainer(player.inventory, world, pos, (BTileEntity)te)) : null; }

  public static Object getClientGuiElement(final EntityPlayer player, final World world, final BlockPos pos, final TileEntity te)
  { return (te instanceof BTileEntity) ? (new BGui(player.inventory, world, pos, (BTileEntity)te)) : null; }

  //--------------------------------------------------------------------------------------------------------------------
  // Crafting history
  //--------------------------------------------------------------------------------------------------------------------

  private static class CraftingHistory
  {
    public static final int RESULT_STACK_INDEX = 0;
    public static final int INPUT_STACKS_BEGIN = 1;
    public static final List<ItemStack> NOTHING = new ArrayList<ItemStack>();
    private static int max_history_size_ = 5;

    private List<String> history_ = new ArrayList<String>();
    private int current_ = -1;
    private List<ItemStack> current_stacks_ = new ArrayList<ItemStack>();
    private IRecipe current_recipe_ = null;

    public CraftingHistory()
    {}

    public static int max_history_size()
    { return max_history_size_; }

    public static int max_history_size(int newsize)
    { return max_history_size_ = MathHelper.clamp(newsize, 0, 32); }

    public void read(final NBTTagCompound nbt)
    {
      try {
        clear();
        final NBTTagCompound subsect = nbt.getCompoundTag("history");
        if(subsect.isEmpty()) return;
        {
          String s = subsect.getString("elements");
          if((s!=null) && (s.length() > 0)) {
            String[] ls = s.split("[|]");
            for(String e:ls) history_.add(e.toLowerCase().trim());
          }
        }
        current_ = (!subsect.hasKey("current")) ? (-1) : MathHelper.clamp(subsect.getInteger("current"), -1, history_.size()-1);
        update_current();
      } catch(Throwable ex) {
        ModEngineersDecor.logger.error("Exception reading crafting table history NBT, resetting, exception is:" + ex.getMessage());
        clear();
      }
    }

    public void write(final NBTTagCompound nbt)
    {
      final NBTTagCompound subsect = new NBTTagCompound();
      subsect.setInteger("current", current_);
      subsect.setString("elements", String.join("|", history_));
      nbt.setTag("history", subsect);
    }

    public void clear()
    { reset_current(); history_.clear(); }

    public void reset_current()
    { current_ = -1; current_stacks_ = NOTHING; current_recipe_ = null; }

    void update_current()
    {
      if((current_ < 0) || (current_ >= history_.size())) { reset_current(); return; }
      Tuple<IRecipe, List<ItemStack>> data = str2stacks(history_.get(current_));
      if(data == null) { reset_current(); return; }
      current_recipe_ = data.getFirst();
      current_stacks_ = data.getSecond();
    }

    public void add(final List<ItemStack> grid_stacks, IRecipe recipe)
    {
      if(!with_assist) { clear(); return; }
      String s = stacks2str(grid_stacks, recipe);
      String recipe_filter = recipe.getRegistryName().toString() + ";";
      if(s.isEmpty()) return;
      history_.removeIf(e->e.equals(s));
      history_.removeIf(e->e.startsWith(recipe_filter));
      history_.add(s);
      while(history_.size() > max_history_size()) history_.remove(0);
      if(current_ >= history_.size()) reset_current();
    }

    public String stacks2str(final List<ItemStack> grid_stacks, IRecipe recipe)
    {
      if((grid_stacks==null) || (grid_stacks.size() != 10) || (recipe==null)) return "";
      if(grid_stacks.get(0).isEmpty()) return "";
      final ArrayList<String> items = new ArrayList<String>();
      items.add(recipe.getRegistryName().toString().trim());
      for(ItemStack st:grid_stacks) {
        int meta = st.getMetadata();
        items.add( (st.isEmpty()) ? ("") : ((st.getItem().getRegistryName().toString().trim()) + ((meta==0)?(""):("/"+meta)) ));
      }
      return String.join(";", items);
    }

    public @Nullable Tuple<IRecipe, List<ItemStack>> str2stacks(final String entry)
    {
      try {
        if((entry == null) || (entry.isEmpty())) return null;
        ArrayList<String> item_regnames = new ArrayList<String>(Arrays.asList(entry.split("[;]")));
        if((item_regnames == null) || (item_regnames.size() < 2) || (item_regnames.size() > 11)) return null;
        while(item_regnames.size() < 11) item_regnames.add("");
        IRecipe recipe = null;
        try {
          final IForgeRegistry<IRecipe> recipe_registry = GameRegistry.findRegistry(IRecipe.class);
          recipe = recipe_registry.getValue(new ResourceLocation(item_regnames.remove(0)));
        } catch(Throwable e) {
          ModEngineersDecor.logger.error("Recipe lookup failed: " + e.getMessage());
        }
        if(recipe==null) return null;
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        for(String regname : item_regnames) {
          ItemStack stack = ItemStack.EMPTY;
          if(!regname.isEmpty()) {
            int meta = 0;
            if(regname.indexOf('/') >= 0) {
              String[] itemdetails = regname.split("[/]");
              if(itemdetails.length>0) regname = itemdetails[0];
              if(itemdetails.length>1) try { meta=Integer.parseInt(itemdetails[1]); } catch(Throwable e){ meta=0; } // ignore exception here
            }
            final Item item = Item.REGISTRY.getObject(new ResourceLocation(regname));
            stack = ((item == null) || (item == Items.AIR)) ? ItemStack.EMPTY : (new ItemStack(item, 1, meta));
          }
          stacks.add(stack);
        }
        if((stacks.size() != 10) || (stacks.get(0).isEmpty())) return null; // invalid size or no result
        return new Tuple<IRecipe, List<ItemStack>>(recipe, stacks);
      } catch(Throwable ex) {
        ModEngineersDecor.logger.error("History stack building failed: " + ex.getMessage());
        return null;
      }
    }

    public List<ItemStack> current()
    { return current_stacks_; }

    public IRecipe current_recipe()
    { return current_recipe_; }

    public void next()
    {
      if(history_.isEmpty()) {
        current_ = -1;
      } else {
        current_ = ((++current_) >= history_.size()) ? (-1) : (current_);
      }
      update_current();
    }

    public void prev()
    {
      if(history_.isEmpty()) {
        current_ = -1;
      } else {
        current_ = ((--current_) < -1) ? (history_.size()-1) : (current_);
      }
      update_current();
    }

    public void reset_selection()
    { current_ = -1; update_current(); }

    public String toString()
    {
      String rec = (current_recipe_==null) ? "none" : (current_recipe_.getRegistryName().toString());
      StringBuilder s = new StringBuilder("{ current:" + current_ + ", recipe:'" + rec + "', elements:[ ");
      for(int i=0; i<history_.size(); ++i) s.append("{i:").append(i).append(", e:[").append(history_.get(i)).append("]} ");
      s.append("]}");
      return s.toString();
    }

  }

  //--------------------------------------------------------------------------------------------------------------------
  // GUI
  //--------------------------------------------------------------------------------------------------------------------

  @SideOnly(Side.CLIENT)
  private static class BGui extends GuiContainer
  {
    protected static final ResourceLocation BACKGROUND = new ResourceLocation(ModEngineersDecor.MODID, "textures/gui/treated_wood_crafting_table.png");
    protected final BTileEntity te;
    protected final EntityPlayer player;
    protected final ArrayList<GuiButton> buttons = new ArrayList<GuiButton>();
    protected final boolean history_slot_tooltip[] = {false,false,false,false,false,false,false,false,false,false};

    public BGui(InventoryPlayer playerInventory, World world, BlockPos pos, BTileEntity te)
    { super(new BContainer(playerInventory, world, pos, te)); this.te = te; this.player=playerInventory.player; }

    @Override
    @SuppressWarnings("unused")
    public void initGui()
    {
      super.initGui();
      final int x0=((width - xSize)/2), y0=((height - ySize)/2);
      buttons.clear();
      if(with_assist) {
        buttons.add(addButton(new GuiButtonImage(BContainer.BUTTON_NEXT,       x0+158,y0+44, 12,12, 194,44, 12, BACKGROUND)));
        buttons.add(addButton(new GuiButtonImage(BContainer.BUTTON_PREV,       x0+158,y0+30, 12,12, 180,30, 12, BACKGROUND)));
        buttons.add(addButton(new GuiButtonImage(BContainer.BUTTON_CLEAR_GRID, x0+158,y0+58, 12,12, 194,8,  12, BACKGROUND)));
        buttons.add(addButton(new GuiButtonImage(BContainer.BUTTON_NEXT_COLLISION_RECIPE, x0+132,y0+18, 20,10, 183,95, 12, BACKGROUND)));
        if(with_assist_quickmove_buttons) {
          buttons.add(addButton(new GuiButtonImage(BContainer.BUTTON_FROM_STORAGE, x0+49, y0+34,  9,17, 219,34, 17, BACKGROUND)));
          buttons.add(addButton(new GuiButtonImage(BContainer.BUTTON_TO_STORAGE,   x0+49, y0+52,  9,17, 208,16, 17, BACKGROUND)));
          buttons.add(addButton(new GuiButtonImage(BContainer.BUTTON_FROM_PLAYER,  x0+77, y0+71, 17,9,  198,71, 9, BACKGROUND)));
          buttons.add(addButton(new GuiButtonImage(BContainer.BUTTON_TO_PLAYER,    x0+59, y0+71, 17,9,  180,71, 9, BACKGROUND)));
        }
      }
    }

    @Override
    public void handleMouseInput() throws IOException
    {
      super.handleMouseInput();
      final int wheel = Mouse.getDWheel();
      if(wheel != 0) {
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = this.height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if(wheel != 0) mouseScrolled(x, y, (wheel>0) ? 1 : -1);
      }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks)
    {
      if(with_assist) {
        buttons.get(BContainer.BUTTON_NEXT_COLLISION_RECIPE).visible = te.has_recipe_collision();
        buttons.get(BContainer.BUTTON_NEXT_COLLISION_RECIPE).enabled = te.has_recipe_collision();
      }
      drawDefaultBackground();
      super.drawScreen(mouseX, mouseY, partialTicks);
      renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY)
    {
      if((!player.inventory.getItemStack().isEmpty()) || (getSlotUnderMouse() == null)) return;
      final Slot slot = getSlotUnderMouse();
      if(!slot.getStack().isEmpty()) { renderToolTip(slot.getStack(), mouseX, mouseY); return; }
      if(with_assist) {
        int hist_index = -1;
        if(slot instanceof BSlotCrafting) {
          hist_index = 0;
        } else if(slot.inventory instanceof BInventoryCrafting) {
          hist_index = slot.getSlotIndex() + 1;
        }
        if((hist_index < 0) || (hist_index >= history_slot_tooltip.length)) return;
        if(!history_slot_tooltip[hist_index]) return;
        ItemStack hist_stack = te.history.current().get(hist_index);
        if(!hist_stack.isEmpty()) renderToolTip(hist_stack, mouseX, mouseY);
      }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY)
    {
      GlStateManager.color(1f, 1f, 1f, 1f);
      mc.getTextureManager().bindTexture(BACKGROUND);
      final int x0=((width - xSize)/2), y0=((height - ySize)/2);
      drawTexturedModalRect(x0, y0, 0, 0, xSize, ySize);
      if(with_assist) {
        for(int i=0; i<history_slot_tooltip.length; ++i) history_slot_tooltip[i] = false;
        List<ItemStack> crafting_template = te.history.current();
        if((crafting_template == null) || (crafting_template.isEmpty())) return;
        {
          int i = 0;
          for(Tuple<Integer, Integer> e : ((BContainer) inventorySlots).CRAFTING_SLOT_POSITIONS) {
            if(i==0) continue; // explicitly here, that is the result slot.
            if((inventorySlots.getSlot(i).getHasStack())) {
              if(!inventorySlots.getSlot(i).getStack().isItemEqual(crafting_template.get(i))) {
                return; // user has placed another recipe
              }
            }
            ++i;
          }
        }
        {
          int i = 0;
          for(Tuple<Integer, Integer> e : ((BContainer) inventorySlots).CRAFTING_SLOT_POSITIONS) {
            final ItemStack stack = crafting_template.get(i);
            if(!stack.isEmpty()) {
              if(!inventorySlots.getSlot(i).getHasStack()) history_slot_tooltip[i] = true;
              if((i==0) && inventorySlots.getSlot(i).getStack().isItemEqual(crafting_template.get(i))) {
                continue; // don't shade the output slot if the result can be crafted.
              } else {
                drawTemplateItemAt(stack, x0, y0, e.getFirst(), e.getSecond());
              }
            }
            ++i;
          }
        }
      }
    }

    protected void drawTemplateItemAt(ItemStack stack, int x0, int y0, int x, int y)
    {
      final float main_zl = zLevel;
      RenderHelper.disableStandardItemLighting();
      RenderHelper.enableGUIStandardItemLighting();
      final float zl = itemRender.zLevel;
      itemRender.zLevel = -50;
      itemRender.renderItemIntoGUI(stack, x0+x, y0+y);
      itemRender.zLevel = zl;
      zLevel = 100;
      GlStateManager.color(0.7f, 0.7f, 0.7f, 0.8f);
      mc.getTextureManager().bindTexture(BACKGROUND);
      drawTexturedModalRect(x0+x, y0+y, x, y, 16, 16);
      RenderHelper.enableGUIStandardItemLighting();
      RenderHelper.enableStandardItemLighting();
      zLevel = main_zl;
    }

    @Override
    protected void actionPerformed(GuiButton button)
    {
      switch(button.id) {
        case BContainer.BUTTON_NEXT:
        case BContainer.BUTTON_PREV:
        case BContainer.BUTTON_CLEAR_GRID:
        case BContainer.BUTTON_FROM_STORAGE:
        case BContainer.BUTTON_TO_STORAGE:
        case BContainer.BUTTON_FROM_PLAYER:
        case BContainer.BUTTON_TO_PLAYER:
        case BContainer.ACTION_PLACE_CURRENT_HISTORY_SEL:
        case BContainer.BUTTON_NEXT_COLLISION_RECIPE: {
          NBTTagCompound nbt = new NBTTagCompound();
          nbt.setInteger("action", button.id);
          Networking.PacketTileNotify.sendToServer(te, nbt);
          break;
        }
      }
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotId, int mouseButton, ClickType type)
    {
      if(type == ClickType.PICKUP) {
        boolean place_refab = (slot instanceof BSlotCrafting) && (!slot.getHasStack());
        if(place_refab && with_assist_direct_history_refab) onHistoryItemPlacement(); // place before crafting -> direct item pick
        super.handleMouseClick(slot, slotId, mouseButton, type);
        if(place_refab && (!with_assist_direct_history_refab)) onHistoryItemPlacement(); // place after crafting -> confirmation first
        return;
      }
      if((type == ClickType.QUICK_MOVE) && (slotId > 0) && (slot.getHasStack())) { // container slots 0 is crafting output
        if(with_assist) {
          List<ItemStack> history = te.history.current();
          boolean palce_in_crafting_grid = false;
          if(slotId > 9) { // container slots 1..9 are crafting grid
            palce_in_crafting_grid = (!history.isEmpty());
            if(!palce_in_crafting_grid) {
              for(int i = 0; i < 9; ++i) {
                if(!(te.getStackInSlot(i).isEmpty())) {
                  palce_in_crafting_grid = true;
                  break;
                }
              }
            }
          }
          if(palce_in_crafting_grid) {
            // Explicit grid placement.
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setInteger("action", BContainer.ACTION_PLACE_SHIFTCLICKED_STACK);
            nbt.setInteger("containerslot", slotId);
            if(ModAuxiliaries.isCtrlDown()) nbt.setBoolean("move-all", true);
            Networking.PacketTileNotify.sendToServer(te, nbt);
            return;
          } else if(ModAuxiliaries.isCtrlDown()) {
            // Move all same items from the inventory of the clicked slot
            // (or the crafting grid) to the corresponding target inventory.
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setInteger("action", BContainer.ACTION_MOVE_ALL_STACKS);
            nbt.setInteger("containerslot", slotId);
            Networking.PacketTileNotify.sendToServer(te, nbt);
            return;
          } else {
            // Let the normal slot click handle that.
          }
        }
      }
      super.handleMouseClick(slot, slotId, mouseButton, type);
    }

    private boolean mouseScrolled(int mouseX, int mouseY, int wheel_inc)
    {
      final Slot resultSlot = getSlotUnderMouse();
      if((!with_crafting_slot_mouse_scrolling) || (!(resultSlot instanceof BSlotCrafting))) return false;
      int count = resultSlot.getStack().getCount();
      int limit = (ModAuxiliaries.isShiftDown() ? 2 : 1) * (ModAuxiliaries.isCtrlDown() ? 4 : 1);
      if(wheel_inc > 0) {
        if(count > 0) {
          if((count < resultSlot.getStack().getMaxStackSize()) && (count < resultSlot.getSlotStackLimit())) {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setInteger("action", BContainer.ACTION_INCREASE_CRAFTING_STACKS);
            if(limit > 1) nbt.setInteger("limit", limit);
            Networking.PacketTileNotify.sendToServer(te, nbt);
          }
        } else if(!te.history.current().isEmpty()) {
          NBTTagCompound nbt = new NBTTagCompound();
          nbt.setInteger("action", BContainer.ACTION_PLACE_CURRENT_HISTORY_SEL);
          Networking.PacketTileNotify.sendToServer(te, nbt);
        }
      } else if(wheel_inc < 0) {
        if(count > 0) {
          NBTTagCompound nbt = new NBTTagCompound();
          if(limit > 1) nbt.setInteger("limit", limit);
          nbt.setInteger("action", BContainer.ACTION_DECREASE_CRAFTING_STACKS);
          Networking.PacketTileNotify.sendToServer(te, nbt);
        }
      }
      return true;
    }

    private void onHistoryItemPlacement()
    {
      if(te.history.current().isEmpty()) return;
      final Slot resultSlot = this.getSlotUnderMouse(); // double check
      if(!(resultSlot instanceof BSlotCrafting)) return;
      NBTTagCompound nbt = new NBTTagCompound();
      nbt.setInteger("action", BContainer.ACTION_PLACE_CURRENT_HISTORY_SEL);
      Networking.PacketTileNotify.sendToServer(te, nbt);
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Crafting slot of container
  //--------------------------------------------------------------------------------------------------------------------

  public static class BSlotCrafting extends SlotCrafting
  {
    private final BTileEntity te;
    private final EntityPlayer player;

    public BSlotCrafting(BTileEntity te, EntityPlayer player, InventoryCrafting craftingInventory, IInventory inventoryIn, int slotIndex, int xPosition, int yPosition)
    { super(player, craftingInventory, inventoryIn, slotIndex, xPosition, yPosition); this.te = te; this.player=player; }

    @Override
    protected void onCrafting(ItemStack stack)
    {
      if((with_assist) && ((player.world!=null) && (!(player.world.isRemote))) && (!stack.isEmpty())) {
      final IRecipe recipe = ((InventoryCraftResult)this.inventory).getRecipeUsed();
        final ArrayList<ItemStack> grid = new ArrayList<ItemStack>();
        grid.add(stack);
        for(int i = 0; i < 9; ++i) grid.add(te.stacks.get(i));
        te.history.add(grid, recipe);
        te.history.reset_current();
        te.syncHistory(player);
      }
      super.onCrafting(stack);
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Crafting container
  //--------------------------------------------------------------------------------------------------------------------

  public static class BContainer extends Container
  {
    protected static final int BUTTON_NEXT = 0;
    protected static final int BUTTON_PREV = 1;
    protected static final int BUTTON_CLEAR_GRID = 2;
    protected static final int BUTTON_NEXT_COLLISION_RECIPE = 3;
    protected static final int BUTTON_FROM_STORAGE = 4;
    protected static final int BUTTON_TO_STORAGE = 5;
    protected static final int BUTTON_FROM_PLAYER = 6;
    protected static final int BUTTON_TO_PLAYER = 7;
    protected static final int ACTION_PLACE_CURRENT_HISTORY_SEL = 8;
    protected static final int ACTION_PLACE_SHIFTCLICKED_STACK = 9;
    protected static final int ACTION_MOVE_ALL_STACKS = 10;
    protected static final int ACTION_INCREASE_CRAFTING_STACKS = 11;
    protected static final int ACTION_DECREASE_CRAFTING_STACKS = 12;


    private final World world;
    private final BlockPos pos;
    private final EntityPlayer player;
    private final BTileEntity te;
    public BInventoryCrafting craftMatrix;
    public InventoryCraftResult craftResult = new InventoryCraftResult();
    public final ImmutableList<Tuple<Integer,Integer>> CRAFTING_SLOT_POSITIONS;

    public BContainer(InventoryPlayer playerInventory, World world, BlockPos pos, BTileEntity te)
    {
      ArrayList<Tuple<Integer,Integer>> slotpositions = new ArrayList<Tuple<Integer,Integer>>();
      this.player = playerInventory.player;
      this.world = world;
      this.pos = pos;
      this.te = te;
      craftMatrix = new BInventoryCrafting(this, te);
      craftMatrix.openInventory(player);
      // container slotId 0 === crafting output
      addSlotToContainer(new BSlotCrafting(te, playerInventory.player, craftMatrix, craftResult, 0, 134, 35));
      slotpositions.add(new Tuple<>(134, 35));
      // container slotId 1..9 === TE slots 0..8
      for(int y=0; y<3; ++y) {
        for(int x=0; x<3; ++x) {
          int xpos = 60+x*18;
          int ypos = 17+y*18;
          addSlotToContainer(new Slot(craftMatrix, x+y*3, xpos, ypos));
          slotpositions.add(new Tuple<>(xpos, ypos));
        }
      }
      CRAFTING_SLOT_POSITIONS = ImmutableList.copyOf(slotpositions);
      // container slotId 10..36 === player slots: 9..35
      for(int y=0; y<3; ++y) {
        for(int x=0; x<9; ++x) {
          addSlotToContainer(new Slot(playerInventory, x+y*9+9, 8+x*18, 86+y*18));
        }
      }
      // container slotId 37..45 === player slots: 0..8
      for(int x=0; x<9; ++x) {
        addSlotToContainer(new Slot(playerInventory, x, 8+x*18, 144));
      }
      // container slotId 46..53 === TE slots 9..17 (storage)
      for(int y=0; y<4; ++y) {
        for(int x=0; x<2; ++x) {
          addSlotToContainer(new Slot(craftMatrix, x+y*2+9, 8+x*18, 9+y*18));
        }
      }
      onCraftMatrixChanged(craftMatrix);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player)
    { return (world.getBlockState(pos).getBlock() instanceof BlockDecorCraftingTable) && (player.getDistanceSq(pos) <= 64); }

    @Override
    public void onCraftMatrixChanged(IInventory inv)
    {
      if(world.isRemote) return;
      try {
        InventoryCrafting grid = craftMatrix;
        InventoryCraftResult result = craftResult;
        EntityPlayerMP pl = (EntityPlayerMP)player;
        ItemStack stack = ItemStack.EMPTY;
        List<IRecipe> matching_recipes = new ArrayList<IRecipe>();
        final IRecipe current_recipe = result.getRecipeUsed();
        final ItemStack current_recipe_stack = result.getStackInSlot(0);
        IRecipe current_recipe_matching = null;
        for(IRecipe r:CraftingManager.REGISTRY) {
          if((r==null) || (!r.matches(grid, world))) continue;
          if((!r.isDynamic()) && (world.getGameRules().getBoolean("doLimitedCrafting")) && (!pl.getRecipeBook().isUnlocked(r))) continue;
          matching_recipes.add(r);
          ItemStack recipe_result_stack = r.getCraftingResult(grid);
          if((r==current_recipe) || (recipe_result_stack.isItemEqual(current_recipe_stack))) current_recipe_matching = r;
        }
        te.has_recipe_collision_ = (matching_recipes.size() > 1);
        if(matching_recipes.size() > 0) {
          if(current_recipe_matching==null) current_recipe_matching = matching_recipes.get(0);
          result.setRecipeUsed(current_recipe_matching);
          stack = current_recipe_matching.getCraftingResult(grid);
        }
        result.setInventorySlotContents(0, stack);
        pl.connection.sendPacket(new SPacketSetSlot(this.windowId, 0, stack));
        te.syncProperties(player);
      } catch(Throwable exc) {
        ModEngineersDecor.logger.error("Recipe failed:", exc);
      }
    }

    @Override
    public void onContainerClosed(EntityPlayer player)
    {
      craftMatrix.closeInventory(player);
      craftResult.clear();
      craftResult.closeInventory(player);
      if(player!=null) {
        for(Slot e:player.inventoryContainer.inventorySlots) {
          if(e instanceof SlotCrafting) {
            ((SlotCrafting)e).putStack(ItemStack.EMPTY);
          }
        }
      }
    }

    @Override
    public boolean canMergeSlot(ItemStack stack, Slot slot)
    { return (slot.inventory != craftResult) && (super.canMergeSlot(stack, slot)); }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index)
    {
      Slot slot = inventorySlots.get(index);
      if((slot == null) || (!slot.getHasStack())) return ItemStack.EMPTY;
      ItemStack slotstack = slot.getStack();
      ItemStack stack = slotstack.copy();
      if(index == 0) {
        slotstack.getItem().onCreated(slotstack, this.world, playerIn);
        if(!this.mergeItemStack(slotstack, 10, 46, true)) return ItemStack.EMPTY;
        slot.onSlotChange(slotstack, stack);
      } else if(index >= 10 && (index < 46)) {
        if(!this.mergeItemStack(slotstack, 46, 54, false)) return ItemStack.EMPTY;
      } else if((index >= 46) && (index < 54)) {
        if(!this.mergeItemStack(slotstack, 10, 46, false)) return ItemStack.EMPTY;
      } else if(!this.mergeItemStack(slotstack, 10, 46, false)) {
        return ItemStack.EMPTY;
      }
      if(slotstack.isEmpty()) {
        slot.putStack(ItemStack.EMPTY);
      } else {
        slot.onSlotChanged();
      }
      if(slotstack.getCount() == stack.getCount()) {
        return ItemStack.EMPTY;
      }
      ItemStack itemstack2 = slot.onTake(playerIn, slotstack);
      if(index == 0) {
        playerIn.dropItem(itemstack2, false);
      }
      return stack;
    }

    public void setCraftingMatrixSlot(int slot_index, ItemStack stack)
    { craftMatrix.setInventorySlotContents(slot_index, stack.copy()); }

    public void select_next_collision_recipe(IInventory inv, EntityPlayer player)
    {
      if(world.isRemote) return;
      try {
        EntityPlayerMP pl = (EntityPlayerMP) player;
        List<IRecipe> matching_recipes = new ArrayList<IRecipe>();
        final IRecipe current_recipe = craftResult.getRecipeUsed();
        final ItemStack current_recipe_stack = craftResult.getStackInSlot(0);
        int next_recipe_index = 0;
        for(IRecipe r:CraftingManager.REGISTRY) {
          if((r==null) || (!r.matches(craftMatrix, world))) continue;
          if((!r.isDynamic()) && (world.getGameRules().getBoolean("doLimitedCrafting")) && (!pl.getRecipeBook().isUnlocked(r))) continue;
          matching_recipes.add(r);
          ItemStack recipe_result_stack = r.getCraftingResult(craftMatrix);
          if((r==current_recipe) || (recipe_result_stack.isItemEqual(current_recipe_stack))) next_recipe_index = matching_recipes.size();
        }
        IRecipe next_recipe = matching_recipes.get((next_recipe_index >= matching_recipes.size()) ? 0 : next_recipe_index);
        craftResult.setInventorySlotContents(0, next_recipe.getCraftingResult(craftMatrix));
        craftResult.setRecipeUsed(next_recipe);
        onCraftMatrixChanged(inv);
      } catch(Throwable exc) {
        ModEngineersDecor.logger.error("Recipe failed:", exc);
      }
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Crafting inventory (needed to allow SlotCrafting to have a InventoryCrafting)
  //--------------------------------------------------------------------------------------------------------------------

  private static class BInventoryCrafting extends InventoryCrafting
  {
    protected final Container container;
    protected final IInventory inventory;

    public BInventoryCrafting(Container container_, IInventory inventory_te) {
      super(container_, 3, 3);
      container = container_;
      inventory = inventory_te;
    }

    @Override
    public int getSizeInventory()
    { return 9; }

    @Override
    public void openInventory(EntityPlayer player)
    { inventory.openInventory(player); }

    @Override
    public void closeInventory(EntityPlayer player)
    { inventory.closeInventory(player); }

    @Override
    public void markDirty()
    { inventory.markDirty(); }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack)
    {
      inventory.setInventorySlotContents(index, stack);
      container.onCraftMatrixChanged(this);
    }

    @Override
    public ItemStack getStackInSlot(int index)
    { return inventory.getStackInSlot(index); }

    @Override
    public ItemStack decrStackSize(int index, int count)
    {
      final ItemStack stack = inventory.decrStackSize(index, count);
      if(!stack.isEmpty()) container.onCraftMatrixChanged(this);
      return stack;
    }
  }

  private static class SlotRange
  {
    public final IInventory inventory;
    public int start_slot, end_slot;
    public SlotRange(IInventory inv, int start, int end) { inventory=inv; start_slot=start; end_slot=end; }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Tile entity
  //--------------------------------------------------------------------------------------------------------------------
  public static class BTileEntity extends TileEntity implements IInventory, Networking.IPacketReceiver
  {
    public static final int CRAFTING_SLOTS_BEGIN = 0;
    public static final int NUM_OF_CRAFTING_SLOTS = 9;
    public static final int STORAGE_SLOTS_BEGIN = NUM_OF_CRAFTING_SLOTS;
    public static final int NUM_OF_STORAGE_SLOTS = 8;
    public static final int NUM_OF_SLOTS = NUM_OF_CRAFTING_SLOTS+NUM_OF_STORAGE_SLOTS;
    protected NonNullList<ItemStack> stacks;
    protected final CraftingHistory history = new CraftingHistory();
    protected boolean has_recipe_collision_ = false;

    public BTileEntity()
    { stacks = NonNullList.<ItemStack>withSize(NUM_OF_SLOTS, ItemStack.EMPTY); }

    public void reset()
    { stacks = NonNullList.<ItemStack>withSize(NUM_OF_SLOTS, ItemStack.EMPTY); }

    public void readnbt(NBTTagCompound compound)
    {
      reset();
      ItemStackHelper.loadAllItems(compound, stacks);
      while(stacks.size() < NUM_OF_SLOTS) stacks.add(ItemStack.EMPTY);
      history.read(compound);
    }

    private void writenbt(NBTTagCompound compound)
    {
      ItemStackHelper.saveAllItems(compound, stacks);
      history.write(compound);
    }

    // private aux methods ---------------------------------------------------------------------

    enum PlacementResult { UNCHANGED, INCOMPLETE, PLACED }

    private boolean has_recipe_collision()
    { return has_recipe_collision_; }

    private boolean itemstack_recipe_match(ItemStack grid_stack, ItemStack history_stack)
    {
      if(history.current_recipe()!=null) {
        boolean grid_match, dist_match;
        for(final Ingredient ingredient:history.current_recipe().getIngredients()) {
          grid_match = false; dist_match = false;
          for(final ItemStack match:ingredient.getMatchingStacks()) {
            if(match.isItemEqualIgnoreDurability(grid_stack)) dist_match = true;
            if(match.isItemEqualIgnoreDurability(history_stack)) grid_match = true;
            if(dist_match && grid_match) return true;
          }
        }
      }
      return false;
    }

    private List<ItemStack> refab_crafting_stacks()
    {
      final ArrayList<ItemStack> slots = new ArrayList<ItemStack>();
      final List<ItemStack> tocraft = history.current();
      final int stack_sizes[] = {-1,-1,-1,-1,-1,-1,-1,-1,-1};
      if(tocraft.isEmpty()) return slots;
      for(int i=0; i<9; ++i) {
        if((i+CraftingHistory.INPUT_STACKS_BEGIN) >= tocraft.size()) break;
        final ItemStack needed = tocraft.get(i+CraftingHistory.INPUT_STACKS_BEGIN);
        final ItemStack palced = getStackInSlot(i+CRAFTING_SLOTS_BEGIN);
        if(needed.isEmpty() && (!palced.isEmpty())) return slots; // history vs grid mismatch.
        if((!palced.isEmpty()) && (!itemstack_recipe_match(needed, palced))) return slots; // also mismatch
        if(!needed.isEmpty()) stack_sizes[i] = palced.getCount();
      }
      int min_placed = 64, max_placed=0;
      for(int i=0; i<9; ++i) { // todo: check if java has something like std::accumulate<>()
        if(stack_sizes[i] < 0) continue;
        min_placed = Math.min(min_placed, stack_sizes[i]);
        max_placed = Math.max(max_placed, stack_sizes[i]);
      }
      int fillup_size = (max_placed <= min_placed) ?  (min_placed + 1) : max_placed;
      for(int i=0; i<9; ++i) {
        if(stack_sizes[i] < 0) continue;
        if(fillup_size > getStackInSlot(i+CRAFTING_SLOTS_BEGIN).getMaxStackSize()) return slots; // can't fillup all
      }
      for(int i=0; i<9; ++i) {
        if(stack_sizes[i] < 0) {
          slots.add(ItemStack.EMPTY);
        } else {
          ItemStack st = getStackInSlot(i+CRAFTING_SLOTS_BEGIN).copy();
          if(st.isEmpty()) {
            st = tocraft.get(i+CraftingHistory.INPUT_STACKS_BEGIN).copy();
            st.setCount(Math.min(st.getMaxStackSize(), fillup_size));
          } else {
            st.setCount(MathHelper.clamp(fillup_size-st.getCount(), 0, st.getMaxStackSize()));
          }
          slots.add(st);
        }
      }
      return slots;
    }

    private List<ItemStack> incr_crafting_grid_stacks(int count)
    {
      final ArrayList<ItemStack> stacks = new ArrayList<ItemStack>();
      for(int i=0; i<9; ++i) {
        final ItemStack palced = getStackInSlot(i+CRAFTING_SLOTS_BEGIN).copy();
        if(!palced.isEmpty()) palced.setCount(count);
        stacks.add(palced);
      }
      return stacks;
    }

    /**
     * Moves as much items from the stack to the slots in range [first_slot, last_slot] of the inventory,
     * filling up existing stacks first, then (player inventory only) checks appropriate empty slots next
     * to stacks that have that item already, and last uses any empty slot that can be found.
     * Returns the stack that is still remaining in the referenced `stack`.
     */
    private ItemStack move_stack_to_inventory(final ItemStack stack_to_move, SlotRange range, boolean only_fillup, int limit)
    {
      final IInventory inventory = range.inventory;
      final int slot_begin = range.start_slot;
      final int slot_end = range.end_slot;
      final ItemStack mvstack = stack_to_move.copy();
      if((mvstack.isEmpty()) || (slot_begin < 0) || (slot_end > inventory.getSizeInventory())) return mvstack;
      int limit_left = (limit>0) ? (Math.min(limit, mvstack.getMaxStackSize())) : (mvstack.getMaxStackSize());
      // first iteration: fillup existing stacks
      for(int i = slot_begin; i < slot_end; ++i) {
        final ItemStack stack = inventory.getStackInSlot(i);
        if((stack.isEmpty()) || (!stack.isItemEqual(mvstack))) continue;
        int nmax = Math.min(limit_left, stack.getMaxStackSize() - stack.getCount());
        if(mvstack.getCount() <= nmax) {
          stack.setCount(stack.getCount()+mvstack.getCount());
          mvstack.setCount(0);
          inventory.setInventorySlotContents(i, stack);
          return mvstack;
        } else {
          stack.grow(nmax);
          mvstack.shrink(nmax);
          inventory.setInventorySlotContents(i, stack);
          limit_left -= nmax;
        }
      }
      if(only_fillup) return mvstack;
      if(inventory instanceof InventoryPlayer) {
        // second iteration: use appropriate empty slots
        for(int i = slot_begin+1; i < slot_end-1; ++i) {
          final ItemStack stack = inventory.getStackInSlot(i);
          if(!stack.isEmpty()) continue;
          if((!inventory.getStackInSlot(i+1).isItemEqual(mvstack)) && (!inventory.getStackInSlot(i-1).isItemEqual(mvstack))) continue;
          int nmax = Math.min(limit_left, mvstack.getCount());
          ItemStack placed = mvstack.copy();
          placed.setCount(nmax);
          mvstack.shrink(nmax);
          inventory.setInventorySlotContents(i, placed);
          return mvstack;
        }
      }
      // third iteration: use any empty slots
      for(int i = slot_begin; i < slot_end; ++i) {
        final ItemStack stack = inventory.getStackInSlot(i);
        if(!stack.isEmpty()) continue;
        int nmax = Math.min(limit_left, mvstack.getCount());
        ItemStack placed = mvstack.copy();
        placed.setCount(nmax);
        mvstack.shrink(nmax);
        inventory.setInventorySlotContents(i, placed);
        return mvstack;
      }
      return mvstack;
    }

    private boolean isItemExactlyEqual(ItemStack stack1, ItemStack stack2)
    {
      if(!stack1.isItemEqual(stack2)) return false;
      if(stack1.hasTagCompound()) {
        final NBTTagCompound nbt = stack1.getTagCompound();
        int n = stack1.getTagCompound().getSize();
        if((n > 0) && stack1.getItem().isDamageable() && (stack1.getTagCompound().hasKey("Damage"))) --n;
        if(n > 0) return false;
      }
      if(stack2.hasTagCompound()) {
        final NBTTagCompound nbt = stack2.getTagCompound();
        int n = stack2.getTagCompound().getSize();
        if((n > 0) && stack2.getItem().isDamageable() && (stack2.getTagCompound().hasKey("Damage"))) --n;
        if(n > 0) return false;
      }
      return true;
    }

    /**
     * Moves as much items from the slots in range [first_slot, last_slot] of the inventory into a new stack.
     * Implicitly shrinks the inventory stacks and the `request_stack`.
     */
    private ItemStack move_stack_from_inventory(SlotRange range, final ItemStack request_stack)
    {
      final IInventory inventory = range.inventory;
      final int slot_begin = range.start_slot;
      final int slot_end = range.end_slot;
      ItemStack fetched_stack = request_stack.copy();
      fetched_stack.setCount(0);
      int n_left = request_stack.getCount();
      while(n_left > 0) {
        int smallest_stack_size = 0;
        int smallest_stack_index = -1;
        for(int i = slot_begin; i < slot_end; ++i) {
          final ItemStack stack = inventory.getStackInSlot(i);
          if((!stack.isEmpty()) && (stack.isItemEqual(request_stack))) {
            // Never automatically place stuff with nbt (except a few allowed like "Damage"),
            // as this could be a full crate, a valuable tool item, etc. For these recipes
            // the user has to place this item manually.
            if(stack.hasTagCompound()) {
              final NBTTagCompound nbt = stack.getTagCompound();
              int n = nbt.getSize();
              if((n > 0) && (nbt.hasKey("Damage"))) --n;
              if(n > 0) continue;
            }
            fetched_stack = stack.copy(); // copy exact stack with nbt and tool damage, otherwise we have an automagical repair of items.
            fetched_stack.setCount(0);
            int n = stack.getCount();
            if((n < smallest_stack_size) || (smallest_stack_size <= 0)) {
              smallest_stack_size = n;
              smallest_stack_index = i;
            }
          }
        }
        if(smallest_stack_index < 0) {
          break; // no more items available
        } else {
          int n = Math.min(n_left, smallest_stack_size);
          n_left -= n;
          fetched_stack.grow(n);
          ItemStack st = inventory.getStackInSlot(smallest_stack_index);
          st.shrink(n);
          inventory.setInventorySlotContents(smallest_stack_index, st);
        }
      }
      return fetched_stack;
    }

    private boolean clear_grid_to_storage(EntityPlayer player)
    {
      boolean changed = false;
      for(int grid_i = CRAFTING_SLOTS_BEGIN; grid_i < (CRAFTING_SLOTS_BEGIN+NUM_OF_CRAFTING_SLOTS); ++grid_i) {
        ItemStack stack = getStackInSlot(grid_i);
        if(stack.isEmpty()) continue;
        ItemStack remaining = move_stack_to_inventory(stack, new SlotRange(this, STORAGE_SLOTS_BEGIN, STORAGE_SLOTS_BEGIN+NUM_OF_STORAGE_SLOTS), false, 0);
        setInventorySlotContents(grid_i, remaining);
        changed = true;
      }
      return changed;
    }

    private boolean clear_grid_to_player(EntityPlayer player)
    {
      boolean changed = false;
      for(int grid_i = CRAFTING_SLOTS_BEGIN; grid_i < (CRAFTING_SLOTS_BEGIN+NUM_OF_CRAFTING_SLOTS); ++grid_i) {
        ItemStack remaining = getStackInSlot(grid_i);
        if(remaining.isEmpty()) continue;
        remaining = move_stack_to_inventory(remaining, new SlotRange(player.inventory,9, 36), true, 0); // prefer filling up inventory stacks
        remaining = move_stack_to_inventory(remaining, new SlotRange(player.inventory,0, 9), true, 0);  // then fill up the hotbar stacks
        remaining = move_stack_to_inventory(remaining, new SlotRange(player.inventory,9, 36), false, 0); // then allow empty stacks in inventory
        remaining = move_stack_to_inventory(remaining, new SlotRange(player.inventory,0, 9), false, 0);  // then new stacks in the hotbar
        setInventorySlotContents(grid_i, remaining);
        changed = true;
      }
      return changed;
    }

    private PlacementResult place_stacks(final SlotRange[] ranges, final List<ItemStack> to_fill, @Nullable EntityPlayer player)
    {
      boolean slots_changed = false;
      if(!to_fill.isEmpty()) {
        for(SlotRange slot_range: ranges) {
          for(int it_guard=63; it_guard>=0; --it_guard) {
            boolean slots_updated = false;
            for(int i = 0; i < 9; ++i) {
              if(to_fill.get(i).isEmpty()) continue;
              ItemStack grid_stack = getStackInSlot(i + CRAFTING_SLOTS_BEGIN).copy();
              if(grid_stack.getCount() >= grid_stack.getMaxStackSize()) continue;
              final ItemStack req_stack = to_fill.get(i).copy();
              req_stack.setCount(1);
              final ItemStack mv_stack = move_stack_from_inventory(slot_range, req_stack);
              if(mv_stack.isEmpty()) continue;
              to_fill.get(i).shrink(1);
              if(grid_stack.isEmpty()) {
                grid_stack = mv_stack.copy();
              } else {
                grid_stack.grow(mv_stack.getCount());
              }
              setInventorySlotContents(i + CRAFTING_SLOTS_BEGIN, grid_stack);
              slots_changed = true;
              slots_updated = true;
            }
            if(!slots_updated) break;
          }
        }
      }
      boolean missing_item = false;
      for(ItemStack st:to_fill) {
        if(!st.isEmpty()) {
          missing_item = true;
          break;
        }
      }
      if((history.current_recipe() != null) && (player!=null) && (player.openContainer instanceof BContainer)) {
        ((BContainer)player.openContainer).craftResult.setRecipeUsed(history.current_recipe());
      }
      if(!slots_changed) {
        return PlacementResult.UNCHANGED;
      } else if(missing_item) {
        return PlacementResult.INCOMPLETE;
      } else {
        return PlacementResult.PLACED;
      }
    }

    private PlacementResult distribute_stack(IInventory inventory, final int slotno)
    {
      List<ItemStack> to_refab = refab_crafting_stacks();
      ItemStack to_distribute = inventory.getStackInSlot(slotno).copy();
      if(to_distribute.isEmpty()) return PlacementResult.UNCHANGED;
      int matching_grid_stack_sizes[] = {-1,-1,-1,-1,-1,-1,-1,-1,-1};
      int max_matching_stack_size = -1;
      int min_matching_stack_size = 65;
      int total_num_missing_stacks = 0;
      for(int i=0; i<9; ++i) {
        final ItemStack grid_stack = getStackInSlot(i + CRAFTING_SLOTS_BEGIN);
        final ItemStack refab_stack = to_refab.isEmpty() ? ItemStack.EMPTY : to_refab.get(i).copy();
        if((!grid_stack.isEmpty()) && (isItemExactlyEqual(grid_stack, to_distribute))) {
          matching_grid_stack_sizes[i] = grid_stack.getCount();
          total_num_missing_stacks += grid_stack.getMaxStackSize()-grid_stack.getCount();
          if(max_matching_stack_size < matching_grid_stack_sizes[i]) max_matching_stack_size = matching_grid_stack_sizes[i];
          if(min_matching_stack_size > matching_grid_stack_sizes[i]) min_matching_stack_size = matching_grid_stack_sizes[i];
        } else if((!refab_stack.isEmpty()) && (isItemExactlyEqual(refab_stack,to_distribute))) {
          matching_grid_stack_sizes[i] = 0;
          total_num_missing_stacks += grid_stack.getMaxStackSize();
          if(max_matching_stack_size < matching_grid_stack_sizes[i]) max_matching_stack_size = matching_grid_stack_sizes[i];
          if(min_matching_stack_size > matching_grid_stack_sizes[i]) min_matching_stack_size = matching_grid_stack_sizes[i];
        } else if(grid_stack.isEmpty() && (!refab_stack.isEmpty())) {
          if(itemstack_recipe_match(to_distribute, refab_stack)) {
            matching_grid_stack_sizes[i] = 0;
            total_num_missing_stacks += grid_stack.getMaxStackSize();
            if(max_matching_stack_size < matching_grid_stack_sizes[i]) max_matching_stack_size = matching_grid_stack_sizes[i];
            if(min_matching_stack_size > matching_grid_stack_sizes[i]) min_matching_stack_size = matching_grid_stack_sizes[i];
          }
        }
      }
      if(min_matching_stack_size < 0) return PlacementResult.UNCHANGED;
      final int stack_limit_size = Math.min(to_distribute.getMaxStackSize(), getInventoryStackLimit());
      if(min_matching_stack_size >= stack_limit_size) return PlacementResult.UNCHANGED;
      int n_to_distribute = to_distribute.getCount();
      for(int it_guard=63; it_guard>=0; --it_guard) {
        if(n_to_distribute <= 0) break;
        for(int i=0; i<9; ++i) {
          if(n_to_distribute <= 0) break;
          if(matching_grid_stack_sizes[i] == min_matching_stack_size) {
            ++matching_grid_stack_sizes[i];
            --n_to_distribute;
          }
        }
        if(min_matching_stack_size < max_matching_stack_size) {
          ++min_matching_stack_size; // distribute short stacks
        } else {
          ++min_matching_stack_size; // stacks even, increase all
          max_matching_stack_size = min_matching_stack_size;
        }
        if(min_matching_stack_size >= stack_limit_size) break; // all full
      }
      if(n_to_distribute == to_distribute.getCount()) return PlacementResult.UNCHANGED; // was already full
      if(n_to_distribute <= 0) {
        inventory.setInventorySlotContents(slotno, ItemStack.EMPTY);
      } else {
        to_distribute.setCount(n_to_distribute);
        inventory.setInventorySlotContents(slotno, to_distribute);
      }
      for(int i=0; i<9; ++i) {
        if(matching_grid_stack_sizes[i] < 0) continue;
        ItemStack grid_stack = getStackInSlot(i + CRAFTING_SLOTS_BEGIN).copy();
        if(grid_stack.isEmpty()) grid_stack = to_distribute.copy();
        grid_stack.setCount(matching_grid_stack_sizes[i]);
        setInventorySlotContents(i + CRAFTING_SLOTS_BEGIN, grid_stack);
      }
      return PlacementResult.PLACED;
    }

    private boolean decrease_grid_stacks(SlotRange[] ranges, int limit)
    {
      boolean changed = false;
      for(int i=0; i<9; ++i) {
        ItemStack stack = getStackInSlot(i+CRAFTING_SLOTS_BEGIN).copy();
        if(stack.isEmpty()) continue;
        for(SlotRange range:ranges) {
          ItemStack remaining = move_stack_to_inventory(stack, range, false, limit);
          if(remaining.getCount() < stack.getCount()) changed = true;
          boolean stop = (remaining.getCount() <= Math.max(0, (stack.getCount()-limit)));
          stack = remaining;
          if(stop) break;
        }
        setInventorySlotContents(i+CRAFTING_SLOTS_BEGIN, stack.isEmpty() ? ItemStack.EMPTY : stack);
      }
      return changed;
    }

    private boolean increase_grid_stacks(SlotRange[] ranges, int limit, EntityPlayer player)
    { return place_stacks(ranges, incr_crafting_grid_stacks(limit), player) != PlacementResult.UNCHANGED; }

    // Networking.IPacketReceiver --------------------------------------------------------------

    @Override
    public void onClientPacketReceived(EntityPlayer player, NBTTagCompound nbt)
    {
      if((world.isRemote) || (!(player.openContainer instanceof BContainer))) return;
      final BContainer container = (BContainer)player.openContainer;
      if(container.te != this) return;
      boolean te_changed = false;
      boolean player_inventory_changed = false;
      if(with_assist && nbt.hasKey("action")) {
        switch(nbt.getInteger("action")) {
          case BContainer.BUTTON_NEXT: {
            history.next();
            syncHistory(player);
            // implicitly clear the grid, so that the player can see the refab, and that no recipe is active.
            if(clear_grid_to_player(player)) { te_changed = true; player_inventory_changed = true; }
            if(clear_grid_to_storage(player)) te_changed = true;
          } break;
          case BContainer.BUTTON_PREV: {
            history.prev();
            syncHistory(player);
            if(clear_grid_to_player(player)) { te_changed = true; player_inventory_changed = true; }
            if(clear_grid_to_storage(player)) te_changed = true;
          } break;
          case BContainer.BUTTON_CLEAR_GRID: {
            history.reset_selection();
            syncHistory(player);
            if(clear_grid_to_player(player)) { te_changed = true; player_inventory_changed = true; }
            if(clear_grid_to_storage(player)) te_changed = true;
          } break;
          case BContainer.BUTTON_TO_STORAGE: {
            if(clear_grid_to_storage(player)) te_changed = true;
          } break;
          case BContainer.BUTTON_TO_PLAYER: {
            if(clear_grid_to_player(player)) { te_changed = true; player_inventory_changed = true; }
          } break;
          case BContainer.BUTTON_FROM_STORAGE: {
            if(place_stacks(new SlotRange[]{
              new SlotRange(this, STORAGE_SLOTS_BEGIN, STORAGE_SLOTS_BEGIN+NUM_OF_STORAGE_SLOTS)
            }, refab_crafting_stacks(), player) != PlacementResult.UNCHANGED) {
              te_changed = true;
            }
          } break;
          case BContainer.BUTTON_FROM_PLAYER: {
            if(place_stacks(new SlotRange[]{
              new SlotRange(player.inventory, 9, 36),
              new SlotRange(player.inventory, 0, 9)
            }, refab_crafting_stacks(), player) != PlacementResult.UNCHANGED) {
              te_changed = true; player_inventory_changed = true;
            }
          } break;
          case BContainer.ACTION_PLACE_CURRENT_HISTORY_SEL: {
            if(place_stacks(new SlotRange[]{
              new SlotRange(player.inventory, 0, 9),
              new SlotRange(player.inventory, 9, 36),
              new SlotRange(this, STORAGE_SLOTS_BEGIN, STORAGE_SLOTS_BEGIN+NUM_OF_STORAGE_SLOTS)
            }, refab_crafting_stacks(), player) != PlacementResult.UNCHANGED) {
              te_changed = true;
            }
          } break;
          case BContainer.ACTION_PLACE_SHIFTCLICKED_STACK: {
            final int container_slot_id = nbt.getInteger("containerslot");
            if((container_slot_id < 10) || (container_slot_id > 53)) {
              break; // out of range
            }
            if(container_slot_id >= 46) {
              // from storage
              final int storage_slot = container_slot_id - 46 + STORAGE_SLOTS_BEGIN;
              PlacementResult stat = distribute_stack(this, storage_slot);
              if(stat != PlacementResult.UNCHANGED) te_changed = true;
            } else {
              // from player
              int player_slot = (container_slot_id >= 37) ? (container_slot_id-37) : (container_slot_id-10+9);
              final ItemStack reference_stack = player.inventory.getStackInSlot(player_slot).copy();
              if((!reference_stack.isEmpty()) && (distribute_stack(player.inventory, player_slot) != PlacementResult.UNCHANGED)) {
                player_inventory_changed = true;
                te_changed = true;
                if(nbt.hasKey("move-all")) {
                  for(int i=0; i < player.inventory.getSizeInventory(); ++i) {
                    final ItemStack stack = player.inventory.getStackInSlot(i);
                    if(!reference_stack.isItemEqual(stack)) continue;
                    if(distribute_stack(player.inventory, i) == PlacementResult.UNCHANGED) break; // grid is full
                  }
                }
              }
            }
          } break;
          case BContainer.ACTION_MOVE_ALL_STACKS: {
            final int container_slot_id = nbt.getInteger("containerslot");
            if((container_slot_id < 1) || (container_slot_id > 53)) {
              break; // out of range
            } else if(container_slot_id < 10) {
              // from crafting grid to player inventory, we clear the grid here as this is most likely
              // what is wanted in the end. Saves clicking the other grid stacks.
              if(clear_grid_to_player(player)) { te_changed = true; player_inventory_changed = true; }
              if(clear_grid_to_storage(player)) te_changed = true;
              break;
            }
            IInventory from_inventory;
            SlotRange[] to_ranges;
            int from_slot;
            if(container_slot_id >= 46) {
              // from storage to player inventory
              from_inventory = this;
              from_slot = container_slot_id - 46 + STORAGE_SLOTS_BEGIN;
              to_ranges = new SlotRange[] {new SlotRange(player.inventory, 9, 36), new SlotRange(player.inventory, 0, 9)};
            } else {
              // from player to storage (otherwise ACTION_PLACE_SHIFTCLICKED_STACK would have been used)
              from_inventory = player.inventory;
              from_slot = (container_slot_id >= 37) ? (container_slot_id-37) : (container_slot_id-10+9);
              to_ranges = new SlotRange[] {new SlotRange(this, STORAGE_SLOTS_BEGIN, STORAGE_SLOTS_BEGIN+NUM_OF_STORAGE_SLOTS)};
            }
            final ItemStack reference_stack = from_inventory.getStackInSlot(from_slot).copy();
            if(!reference_stack.isEmpty()) {
              boolean abort = false;
              for(int i=0; (i < from_inventory.getSizeInventory()) && (!abort); ++i) {
                final ItemStack stack = from_inventory.getStackInSlot(i);
                if(!reference_stack.isItemEqual(stack)) continue;
                ItemStack remaining = from_inventory.getStackInSlot(i);
                for(SlotRange range:to_ranges) {
                  remaining = move_stack_to_inventory(remaining, range, false, 0);
                  if(!remaining.isEmpty()) {
                    abort = true; // no space left
                    break;
                  } else {
                    te_changed = player_inventory_changed = true;
                  }
                }
                from_inventory.setInventorySlotContents(i, remaining);
              }
            }
          } break;
          case BContainer.BUTTON_NEXT_COLLISION_RECIPE: {
            if(player.openContainer instanceof BContainer) {
              ((BContainer)player.openContainer).select_next_collision_recipe(this, player);
            }
          } break;
          case BContainer.ACTION_DECREASE_CRAFTING_STACKS: {
            te_changed = player_inventory_changed = decrease_grid_stacks(new SlotRange[]{
              new SlotRange(player.inventory, 9, 36),
              new SlotRange(player.inventory, 0, 9),
              new SlotRange(this, STORAGE_SLOTS_BEGIN, STORAGE_SLOTS_BEGIN+NUM_OF_STORAGE_SLOTS)
            }, MathHelper.clamp(nbt.getInteger("limit"), 1, 8));
          } break;
          case BContainer.ACTION_INCREASE_CRAFTING_STACKS: {
            te_changed = player_inventory_changed = increase_grid_stacks(new SlotRange[]{
              new SlotRange(player.inventory, 9, 36),
              new SlotRange(player.inventory, 0, 9),
              new SlotRange(this, STORAGE_SLOTS_BEGIN, STORAGE_SLOTS_BEGIN+NUM_OF_STORAGE_SLOTS)
            }, MathHelper.clamp(nbt.getInteger("limit"), 1, 8), player);
          } break;
        }
      }
      if(te_changed) markDirty();
      if(player_inventory_changed) player.inventory.markDirty();
      if(te_changed || player_inventory_changed) {
        container.onCraftMatrixChanged(this);
        container.detectAndSendChanges();
      }
    }

    @Override
    public void onServerPacketReceived(NBTTagCompound nbt)
    {
      if(nbt.hasKey("historydata")) history.read(nbt.getCompoundTag("historydata"));
      if(nbt.hasKey("hascollision")) has_recipe_collision_ = nbt.getBoolean("hascollision");
    }

    private void syncHistory(EntityPlayer player)
    {
      if(!with_assist) return;
      NBTTagCompound history_nbt = new NBTTagCompound();
      history.write(history_nbt);
      NBTTagCompound rnbt = new NBTTagCompound();
      rnbt.setTag("historydata", history_nbt);
      rnbt.setBoolean("hascollision", has_recipe_collision_);
      Networking.PacketTileNotify.sendToPlayer(player, this, rnbt);
    }

    private void syncProperties(EntityPlayer player)
    {
      if(!with_assist) return;
      NBTTagCompound rnbt = new NBTTagCompound();
      rnbt.setBoolean("hascollision", has_recipe_collision_);
      Networking.PacketTileNotify.sendToPlayer(player, this, rnbt);
    }

    // TileEntity ------------------------------------------------------------------------------

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState os, IBlockState ns)
    { return (os.getBlock() != ns.getBlock()) || (!(ns.getBlock() instanceof BlockDecorCraftingTable)); }

    @Override
    public void readFromNBT(NBTTagCompound nbt)
    { super.readFromNBT(nbt); readnbt(nbt); }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt)
    { super.writeToNBT(nbt); writenbt(nbt); return nbt; }

    @Override
    public NBTTagCompound getUpdateTag() // on server
    { NBTTagCompound nbt = new NBTTagCompound(); super.writeToNBT(nbt); writenbt(nbt); return nbt; }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) // on client
    { super.readFromNBT(pkt.getNbtCompound()); readnbt(pkt.getNbtCompound()); super.onDataPacket(net, pkt); }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() // on server
    { return new SPacketUpdateTileEntity(pos, 1, getUpdateTag()); }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) // on client
    { readFromNBT(tag); }

    @Override
    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared()
    { return 400; }

    // IWorldNamable ---------------------------------------------------------------------------

    @Override
    public String getName()
    { final Block block=getBlockType(); return (block!=null) ? (block.getTranslationKey() + ".name") : (""); }

    @Override
    public boolean hasCustomName()
    { return false; }

    @Override
    public ITextComponent getDisplayName()
    { return new TextComponentTranslation(getName(), new Object[0]); }

    // IInventory ------------------------------------------------------------------------------
    // @see net.minecraft.inventory.InventoryCrafting

    @Override
    public int getSizeInventory()
    { return stacks.size(); }

    @Override
    public boolean isEmpty()
    { for(ItemStack stack: stacks) { if(!stack.isEmpty()) return false; } return true; }

    @Override
    public ItemStack getStackInSlot(int index)
    { return (index < getSizeInventory()) ? stacks.get(index) : ItemStack.EMPTY; }

    @Override
    public ItemStack decrStackSize(int index, int count)
    { return ItemStackHelper.getAndSplit(stacks, index, count); }

    @Override
    public ItemStack removeStackFromSlot(int index)
    { return ItemStackHelper.getAndRemove(stacks, index); }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack)
    { stacks.set(index, stack); }

    @Override
    public int getInventoryStackLimit()
    { return 64; }

    @Override
    public void markDirty()
    { super.markDirty(); }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player)
    { return true; }

    @Override
    public void openInventory(EntityPlayer player)
    {}

    @Override
    public void closeInventory(EntityPlayer player)
    { this.markDirty(); }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack)
    { return true; }

    @Override
    public int getField(int id)
    { return 0; }

    @Override
    public void setField(int id, int value)
    {}

    @Override
    public int getFieldCount()
    { return 0; }

    @Override
    public void clear()
    { stacks.clear(); }
  }

}
