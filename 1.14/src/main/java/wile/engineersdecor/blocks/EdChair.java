/*
 * @file EdChair.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2019 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Full block characteristics class.
 */
package wile.engineersdecor.blocks;

import wile.engineersdecor.ModContent;
import wile.engineersdecor.ModEngineersDecor;
import wile.engineersdecor.libmc.blocks.StandardBlocks;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.util.math.*;
import net.minecraft.world.IWorldReader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.FMLPlayMessages;
import net.minecraftforge.fml.network.NetworkHooks;

import java.util.List;
import java.util.Random;


public class EdChair
{
  private static boolean sitting_enabled = true;
  private static double sitting_probability = 0.1;
  private static double standup_probability = 0.01;

  public static void on_config(boolean without_sitting, boolean without_mob_sitting, double sitting_probability_percent, double standup_probability_percent)
  {
    sitting_enabled = (!without_sitting);
    sitting_probability = (without_sitting||without_mob_sitting) ? 0.0 : MathHelper.clamp(sitting_probability_percent/100, 0, 0.9);
    standup_probability = (without_sitting||without_mob_sitting) ? 1.0 : MathHelper.clamp(standup_probability_percent/100, 1e-6, 1e-2);
    ModEngineersDecor.logger().info("Config chairs: " + sitting_enabled + ", sit: " + sitting_probability, ", stand up: " + standup_probability);
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Block
  //--------------------------------------------------------------------------------------------------------------------

  public static class ChairBlock extends StandardBlocks.HorizontalWaterLoggable implements IDecorBlock
  {
    public ChairBlock(long config, Block.Properties builder, final AxisAlignedBB[] unrotatedAABBs)
    { super(config, builder.tickRandomly(), unrotatedAABBs); }

    @Override
    @SuppressWarnings("deprecation")
    public boolean onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult rayTraceResult)
    {
      if(sitting_enabled && (!world.isRemote)) { ChairEntity.sit(world, player, pos); }
      return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity)
    {
      if(sitting_enabled && (Math.random() < sitting_probability) && (entity instanceof MobEntity)) ChairEntity.sit(world, (LivingEntity)entity, pos);
    }

    @Override
    public int tickRate(IWorldReader world)
    { return 10; }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, World world, BlockPos pos, Random rnd)
    {
      if((!sitting_enabled) || (sitting_probability < 1e-6)) return;
      final List<LivingEntity> entities = world.getEntitiesWithinAABB(MobEntity.class, new AxisAlignedBB(pos).grow(2,1,2).expand(0,1,0), e->true);
      if(entities.isEmpty()) return;
      int index = rnd.nextInt(entities.size());
      if((index < 0) || (index >= entities.size())) return;
      ChairEntity.sit(world, entities.get(index), pos);
    }

  }

  //--------------------------------------------------------------------------------------------------------------------
  // Entity
  //--------------------------------------------------------------------------------------------------------------------

  public static class ChairEntity extends Entity
  {
    public static final double x_offset = 0.5d;
    public static final double y_offset = 0.4d;
    public static final double z_offset = 0.5d;
    private int t_sit = 0;
    public BlockPos chair_pos = new BlockPos(0,0,0);

    public ChairEntity(EntityType<? extends Entity> entityType, World world)
    {
      super(entityType, world);
      preventEntitySpawning=true;
      setMotion(Vec3d.ZERO);
      canUpdate(true);
      noClip=true;
    }

    public ChairEntity(World world)
    { this(ModContent.ET_CHAIR, world); }

    public static ChairEntity customClientFactory(FMLPlayMessages.SpawnEntity spkt, World world)
    { return new ChairEntity(world); }

    public IPacket<?> createSpawnPacket()
    { return NetworkHooks.getEntitySpawningPacket(this); }

    public static boolean accepts_mob(LivingEntity entity)
    {
      if(!(entity instanceof net.minecraft.entity.monster.MonsterEntity)) return false;
      if((entity.getType().getSize().height > 2.5) || (entity.getType().getSize().height > 2.0)) return false;
      if(entity instanceof ZombieEntity) return true;
      if(entity instanceof ZombieVillagerEntity) return true;
      if(entity instanceof ZombiePigmanEntity) return true;
      if(entity instanceof HuskEntity) return true;
      if(entity instanceof StrayEntity) return true;
      if(entity instanceof SkeletonEntity) return true;
      if(entity instanceof WitherSkeletonEntity) return true;
      return false;
    }

    public static void sit(World world, LivingEntity sitter, BlockPos pos)
    {
      if(!sitting_enabled) return;
      if((world==null) || (world.isRemote) || (sitter==null) || (pos==null)) return;
      if((!(sitter instanceof PlayerEntity)) && (!accepts_mob(sitter))) return;
      if(!world.getEntitiesWithinAABB(ChairEntity.class, new AxisAlignedBB(pos)).isEmpty()) return;
      if(sitter.isBeingRidden() || (!sitter.isAlive()) || (sitter.isPassenger()) ) return;
      if((!world.isAirBlock(pos.up())) || (!world.isAirBlock(pos.up(2)))) return;
      boolean on_top_of_block_position = true;
      boolean use_next_negative_y_position = false;
      ChairEntity chair = new ChairEntity(world);
      chair.chair_pos = pos;
      chair.t_sit = 5;
      chair.prevPosX = chair.posX;
      chair.prevPosY = chair.posY;
      chair.prevPosZ = chair.posZ;
      chair.setPosition(pos.getX()+x_offset,pos.getY()+y_offset,pos.getZ()+z_offset);
      world.addEntity(chair);
      sitter.startRiding(chair, true);
    }

    @Override
    protected void registerData() {}

    @Override
    protected void readAdditional(CompoundNBT compound) {}

    @Override
    protected void writeAdditional(CompoundNBT compound) {}

    @Override
    protected boolean canTriggerWalking()
    { return false; }

    @Override
    public boolean canBePushed()
    { return false; }

    @Override
    public double getMountedYOffset()
    { return 0.0; }

    @Override
    public void tick()
    {
      if(world.isRemote) return;
      super.tick();
      if(--t_sit > 0) return;
      Entity sitter = getPassengers().isEmpty() ? null : getPassengers().get(0);
      if((sitter==null) || (!sitter.isAlive())) {
        this.remove();
        return;
      }
      boolean abort = (!sitting_enabled);
      final BlockState state = world.getBlockState(chair_pos);
      if((state==null) || (!(state.getBlock() instanceof ChairBlock))) abort = true;
      if(!world.isAirBlock(chair_pos.up())) abort = true;
      if((!(sitter instanceof PlayerEntity)) && (Math.random() < standup_probability)) abort = true;
      if(abort) {
        for(Entity e:getPassengers()) {
          if(e.isAlive()) e.stopRiding();
        }
        this.remove();
      }
    }
  }

}
