package com.SkyIsland.Armory.forge;

import java.util.LinkedList;
import java.util.List;

import com.SkyIsland.Armory.Armory;
import com.SkyIsland.Armory.api.ForgeManager;
import com.SkyIsland.Armory.api.ForgeManager.MetalRecord;
import com.SkyIsland.Armory.blocks.BlockBase;
import com.SkyIsland.Armory.forge.Brazier.BrazierTileEntity;
import com.SkyIsland.Armory.items.HeldMetal;
import com.SkyIsland.Armory.items.MiscItems;
import com.SkyIsland.Armory.items.MiscItems.Items;
import com.SkyIsland.Armory.items.ScrapMetal;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockable;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class Forge extends BlockBase implements ITileEntityProvider {
	
	public static Block block;
	public static Material material;
	public static final String unlocalizedName = "forge_block";
	
	public void clientInit() {
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher()
		.register(Item.getItemFromBlock(this), 0, new ModelResourceLocation(Armory.MODID + ":" + unlocalizedName, "normal"));
	}
	
	public static void preInit() {
		GameRegistry.registerTileEntity(ForgeTileEntity.class, Armory.MODID + "_" + unlocalizedName);
	}
	
	public Forge() {
		super(Material.ground);
		this.blockHardness = 200;
		this.blockResistance = 45;
		this.setStepSound(Block.soundTypeStone);
		this.setUnlocalizedName(Armory.MODID + "_" + unlocalizedName);
        this.setCreativeTab(Armory.creativeTab);
	}

	@Override
	public TileEntity createNewTileEntity(World worldIn, int meta) {
		return new ForgeTileEntity();
	}
	
	@Override
	public boolean onBlockActivated(
			World worldIn, BlockPos pos,
			IBlockState state,
			EntityPlayer playerIn,
			EnumFacing side,
			float hitX,
			float hitY,
			float hitZ) {
		
			// Open GUI!
			if (!worldIn.isRemote) {
			playerIn.openGui(Armory.instance, Armory.Gui_Type.FORGE.ordinal(), worldIn, pos.getX(), pos.getY(), pos.getZ());
			}
			return true;
		}
	
	/**
	 * Sets the forge in the given location to consider the brazier in the given
	 * direction as it's heat source. If <i>relativeDirection</i> is null,
	 * sets the forge to no longer be receiving heat from any brazier.
	 * @param world
	 * @param pos
	 * @param relativeDirection direction <strong> from the forge</strong> to the brazier
	 */
	public static void setBrazier(World world, BlockPos pos, EnumFacing relativeDirection) {
		TileEntity entity = world.getTileEntity(pos);
		if (entity != null && entity instanceof ForgeTileEntity) {
			ForgeTileEntity et = (ForgeTileEntity) entity;
			if (relativeDirection == null) {
				System.out.println("setting to null");
				et.brazierLocation = null;
				return;
			}
			
			
			TileEntity other = world.getTileEntity(pos.offset(relativeDirection));
			if (other != null && other instanceof BrazierTileEntity) {
				//if this is a valid forge location and the given direction points
				//to a valid brazier tile entity
				et.brazierLocation = relativeDirection;
			}
			
		}
	}
	
	public static class ForgeTileEntity extends TileEntityLockable implements ITickable {
		
		protected ItemStack input;
		
		protected EnumFacing brazierLocation;
		
		protected List<ItemStack> meltedItems;
		
		/**
		 * Item that's been taken from input slot, but is still being 'melted'
		 */
		protected ItemStack currentMeltingItem;
		
		/**
		 * Ticks left till currentMeltingItem is finished and can be added to melted items
		 */
		protected int meltingTime;
		
		/**
		 * Updated to attain accurate done-ratio.
		 * Only for displaying stuff, not a mechanical component
		 */
		protected int maxMeltingTime;
		
		public ForgeTileEntity() {
			this.input = null;
			this.brazierLocation = null;
			meltedItems = new LinkedList<ItemStack>();
			currentMeltingItem = null;
			maxMeltingTime = 0;
		}
		
		
		@Override
		public void writeToNBT(NBTTagCompound tag) {
			if (input != null) {
				NBTTagCompound itemTag = new NBTTagCompound();
				input.writeToNBT(itemTag);
				tag.setTag("input", itemTag);
			}

			if (currentMeltingItem != null) {
				NBTTagCompound itemTag = new NBTTagCompound();
				currentMeltingItem.writeToNBT(itemTag);
				tag.setTag("current", itemTag);
			}

			if (meltedItems != null && meltedItems.size() > 0) {
				NBTTagList tagList = new NBTTagList();
				for (ItemStack sub : meltedItems) {
					NBTTagCompound itemTag = new NBTTagCompound();
					sub.writeToNBT(itemTag);
					tagList.appendTag(itemTag);
				}
				tag.setTag("melted", tagList);
			}
			
			if (brazierLocation != null)
				tag.setByte("facing", (byte) brazierLocation.getHorizontalIndex());
			
			tag.setInteger("meltingTime", meltingTime);
			tag.setInteger("maxTime", maxMeltingTime);
			
			super.writeToNBT(tag);
		}
		
		@Override
		public void readFromNBT(NBTTagCompound tag) {
			
			if (tag.hasKey("input", NBT.TAG_COMPOUND)) {
				input = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("input"));
			} else
				this.input = null;
			
			if (tag.hasKey("current")) {
				this.currentMeltingItem = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("current"));
			} else
				this.currentMeltingItem = null;
			
			if (tag.hasKey("meltedItems", NBT.TAG_LIST)) {
				meltedItems = new LinkedList<ItemStack>();
				
				NBTTagList list = tag.getTagList("meltedItems", NBT.TAG_COMPOUND);
				NBTTagCompound sub;
				while (!list.hasNoTags()) {
					sub = (NBTTagCompound) list.removeTag(0);
					meltedItems.add(ItemStack.loadItemStackFromNBT(sub));
				}
			} else {
				meltedItems = null;
			}

			if (tag.hasKey("facing", NBT.TAG_BYTE)) {
				byte index = tag.getByte("facing");
				this.brazierLocation = EnumFacing.getHorizontal(index);
			} else
				this.brazierLocation = null;
			
			meltingTime = tag.getInteger("meltingTime");
			maxMeltingTime = tag.getInteger("maxTime");
			
			super.readFromNBT(tag);
		}
		
		@Override
		public Packet<INetHandlerPlayClient> getDescriptionPacket() {
			NBTTagCompound tagCompound = new NBTTagCompound();
		    writeToNBT(tagCompound);
		    //return new Packet132TileEntityData(xCoord, yCoord, zCoord, 1, tagCompound);
		    return new S35PacketUpdateTileEntity(this.pos, 3, tagCompound);
		}
		
		@Override
		public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt)
	    {
			NBTTagCompound tag = pkt.getNbtCompound();
			readFromNBT(tag);
	    }
		
		@Override
		public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState)
		{
		    return (oldState.getBlock() != newState.getBlock());
//			System.out.println("refresh [" + newState.getBlock() + "]? -> " + !(newState.getBlock() instanceof Brazier));
//			return !(newState.getBlock() instanceof Brazier);
			//return true;
		}
		
		/**
		 * Returns whether this forge can accept and consume the given itemstack
		 * @param item
		 * @return
		 */
		public boolean canAccept(ItemStack item) {
			return (ForgeManager.instance().getMetalRecord(item) != null);
			
		}

		protected void joinBrazier(EnumFacing direction) {
			this.brazierLocation = direction;
		}
		
		protected void breakFromForge() {
			this.brazierLocation = null;
		}
		
		@Override
		public void update() {
			if (meltingTime > 0) {
				meltingTime --;
				
				if (meltingTime == 0) {
					//just finished melting
					addToMelted(currentMeltingItem);
					currentMeltingItem = null;
				}
			}
			
			if (currentMeltingItem == null && input != null) {
				//ready to accept item, if it has enough heat
				if (brazierLocation != null) {
					int heat = getHeat();
					if (heat > -1) {
						//lookup metal record for input
						MetalRecord record = ForgeManager.instance().getMetalRecord(input);
						if (record == null)
							return; //don't accept, since it doens't appear to be
								    //valid input
						
						if (record.getRequiredHeat() <= heat) {
							//process the input
							consumeInput(record.getBurnTime());
						}
						
					}
					
				} //else no heat. Cannot accept new metal
			}
			
		}
		
		/**
		 * Gets heat of current setup. Returns -1 if no heat info is available.
		 * @return
		 */
		private int getHeat() {
			if (brazierLocation == null)
				return -1;
			
			TileEntity ent = getWorld().getTileEntity(pos.offset(brazierLocation));
			if (ent != null && ent instanceof BrazierTileEntity) {
				BrazierTileEntity te = (BrazierTileEntity) ent;
				return te.getHeat();
			}
			
			return -1;
		}
		
		/**
		 * Consumes an item from the input. Does not check that it is possible
		 * to be consumed (heat too low, no metal record, etc). That's up
		 * to you. This is a private method, after all
		 */
		private void consumeInput(int burnTime) {
			currentMeltingItem = input.splitStack(1);
			
			if (input.stackSize <= 0)
				input = null;
			
			meltingTime = burnTime;
			maxMeltingTime = burnTime;
		}
		
		protected void addToMelted(ItemStack melted) {
			this.meltedItems.add(melted);
		}
		
		protected void clearMelted() {
			meltedItems.clear();
		}
		
		protected ItemStack gatherMetals() {
			//get all melted items and put into held metal
			if (meltedItems.isEmpty())
				return null;
			
			boolean same = true;
			Item itemType = null;
			for (ItemStack item : meltedItems) {
				if (itemType == null) {
					itemType = item.getItem();
					continue;
				}
				
				if (itemType != item.getItem()) {
					same = false;
					break;
				}
			}
			
			if (same) {
				//all the same item type
				meltedItems.clear();
				return ((HeldMetal) MiscItems.getItem(Items.HELD_METAL))
						.createStack(meltedItems, getHeat());
			}
			
			//else check if valid alloy
			ItemStack alloy = ForgeManager.instance().getAlloy(meltedItems);
			
			if (alloy == null) {
				//just a bunch of junk. produce scrap.
				//select random item to return
				int index = Armory.random.nextInt(meltedItems.size());
				alloy = ScrapMetal.produceScrap(meltedItems.get(index));
			}
			
			meltedItems.clear();
			return alloy; //if not an alloy, will return scrap. else will return the alloy
			
		}


		@Override
		public Container createContainer(InventoryPlayer playerInventory, EntityPlayer playerIn) {
			// TODO Auto-generated method stub
			return null;
		}


		@Override
		public String getGuiID() {
			return "armory:forge";
		}


		@Override
		public String getName() {
			return "Forge";
		}


		@Override
		public boolean hasCustomName() {
			// TODO Auto-generated method stub
			return false;
		}


		@Override
		public int getSizeInventory() {
			return 1;
		}


		@Override
		public ItemStack getStackInSlot(int index) {
			if (index == 0) {
				return input;
			}
			return null;
		}


		@Override
		public ItemStack decrStackSize(int index, int count) {
			if (index == 0) {
				return input.splitStack(count);
			}
			return null;
		}


		@Override
		public ItemStack removeStackFromSlot(int index) {
			if (index == 0) {
				ItemStack tmp = input;
				input = null;
				return tmp;
			}
			return null;
		}


		@Override
		public void setInventorySlotContents(int index, ItemStack stack) {
			if (index == 0)
				input = stack;
		}


		@Override
		public int getInventoryStackLimit() {
			return 64;
		}


		@Override
		public boolean isUseableByPlayer(EntityPlayer player) {
			return true;
		}


		@Override
		public void openInventory(EntityPlayer player) {
			; //nothing special to do
		}


		@Override
		public void closeInventory(EntityPlayer player) {
			; //nothing spcial to do
		}


		@Override
		public boolean isItemValidForSlot(int index, ItemStack stack) {
			if (index == 0)
				return canAccept(stack);
			
			return false;
		}


		@Override
		public int getField(int id) {
			// TODO Auto-generated method stub
			return 0;
		}


		@Override
		public void setField(int id, int value) {
			// TODO Auto-generated method stub
			
		}


		@Override
		public int getFieldCount() {
			// TODO Auto-generated method stub
			return 0;
		}


		@Override
		public void clear() {
			input = null;
		}
		
	}
	
	public static class ForgeContainer extends Container {
		
		protected ForgeTileEntity forge;
		
		public ForgeContainer(IInventory playerInv, ForgeTileEntity forge) {
			this.forge = forge;
			short slotID = 0;
			
			// Construct slots for player to interact with
			// Brazier only needs '1' inventory slot to interact with
			// Uses slot ID 0
			this.addSlotToContainer(new Slot(forge, 0, 124, 34));
			slotID = 1;
			// Construct player inventory
			for (int y = 0; y < 3; y++) {
				for (int x = 0; x < 9; x++) {
					this.addSlotToContainer(new Slot(playerInv, x + y * 9 + 9, 8 + (x * 18), 84 + (y * 18)));
					slotID++;
				}
			}
			// Construct player hotbar
			for (int x = 0; x < 9; x++) {
				this.addSlotToContainer(new Slot(playerInv, x, 8 + x * 18, 142));
				slotID++;
			}
		}
		
		@Override
		public ItemStack transferStackInSlot(EntityPlayer playerIn, int fromSlot) {
			ItemStack prev = null;	
			Slot slot = (Slot) this.inventorySlots.get(fromSlot);
			
			if (slot != null && slot.getHasStack()) {
				ItemStack cur = slot.getStack();
				prev = cur.copy();
				
				
				/** If we want additional behavior put it here **/
				/**if (fromSlot == 0) {
					// This is going FROM Brazier to player
					if (!this.mergeItemStack(cur, 9, 45, true))
						return null;
					else
						// From Player TO Brazier
						if (!this.mergeItemStack(cur, 0, 0, false)) {
							return null;
						}
				}**/
				
				if (cur.stackSize == 0) {
					slot.putStack((ItemStack) null);
				} else {
					slot.onSlotChanged();
				}
				
				if (cur.stackSize == prev.stackSize) {
					return null;
				}
				slot.onPickupFromSlot(playerIn, cur);
			}
			
			return prev;
		}
		
		@Override
		public boolean canInteractWith(EntityPlayer playerIn) {
			return true;
		}

	}
	
	@SideOnly(Side.CLIENT)
	public static class ForgeGui extends GuiContainer {

		private static final ResourceLocation GuiImageLocation =
				new ResourceLocation(Armory.MODID + ":textures/gui/container/forge.png");
		
		private ForgeContainer forgeContainer;
		
		public ForgeGui(ForgeContainer forge) {
			super(forge);
			this.forgeContainer = forge;
			
		}
		
		@Override
		protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
			//draw progress bar
			if (forgeContainer.forge.meltingTime > 0) {
				float ratio = forgeContainer.forge.meltingTime / forgeContainer.forge.maxMeltingTime;
				ratio = 1 - ratio; //flip it. 10% done is 90% left
				
				if (ratio > .01) {
					int left_x = 84, right_x = 91;
					int y = 62, height = (y - 25);
					int y2 = y - Math.round((float) height * ratio);
					GuiContainer.drawRect(left_x, y2, right_x, y, 0xFF2000);
				}
			}
			
			int horizontalMargin = (width - xSize) / 2;
			int verticalMargin = (height - ySize) / 2;
			
			GlStateManager.color(1.0F,  1.0F, 1.0F, 1.0F);
			mc.getTextureManager().bindTexture(GuiImageLocation);
			int textWidth = 176, textHeight = 166;
			drawTexturedModalRect(horizontalMargin, verticalMargin, 0,0, textWidth, textHeight);
			

			if (forgeContainer.forge.brazierLocation != null) {
				//get brazier
				ForgeTileEntity forge = forgeContainer.forge;
				TileEntity brent = forge.getWorld().getTileEntity(
						forge.getPos().offset(forge.brazierLocation));
				if (brent != null && brent instanceof BrazierTileEntity) {
					int offset = 10;
					int color = 0xFF9728;
					BrazierTileEntity te = (BrazierTileEntity) brent;
					
					if (forge.input != null && forge.canAccept(forge.input)) {
						//check if hot enough
						MetalRecord record = ForgeManager.instance().getMetalRecord(forge.input);
						if (te.getHeat() < record.getRequiredHeat())
							color = 0xFF0000;
						else {
							color = 0x00FF00;
						}
					}
					
					this.fontRendererObj.drawString("Heat:", horizontalMargin + offset, verticalMargin + 20, 0x000000);
					this.fontRendererObj.drawString(te.getHeat() + "", horizontalMargin + offset + this.fontRendererObj.getStringWidth("Heat: "), verticalMargin + 20, color);
				}
			}
			
		}
		
		
	}
}
