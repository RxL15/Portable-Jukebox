package rxl.managers;

import rxl.Jukebox;
import rxl.utils.ItemUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import java.util.ArrayList;
import java.util.List;

public class RecipeManager {
    
    private final Jukebox plugin;
    private final NamespacedKey jukeboxRecipeKey;
    private static final List<Material> PLANK_MATERIALS = initPlankMaterials();
    
    public RecipeManager(Jukebox plugin) {
        this.plugin = plugin;
        this.jukeboxRecipeKey = new NamespacedKey(plugin, "jukebox_recipe");
    }
    
    private static List<Material> initPlankMaterials() {
        List<Material> planks = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.name().endsWith("_PLANKS")) {
                planks.add(material);
            }
        }
        return planks;
    }
    
    public void registerRecipes() {
        registerJukeboxRecipe();
    }
    
    private void registerJukeboxRecipe() {
        ItemStack result = ItemUtil.createJukebox();
        ShapedRecipe recipe = new ShapedRecipe(jukeboxRecipeKey, result);
        recipe.shape("PPP", "PNP", "PRP");
        List<Material> plankMaterials = getAllPlankMaterials();
        recipe.setIngredient('P', new RecipeChoice.MaterialChoice(plankMaterials));
        recipe.setIngredient('N', Material.NOTE_BLOCK);
        recipe.setIngredient('R', Material.REDSTONE);
        plugin.getServer().addRecipe(recipe);
    }
    
    private List<Material> getAllPlankMaterials() {
        return PLANK_MATERIALS;
    }
} 