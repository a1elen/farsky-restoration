package game.gui.menu;

import game.Main;
import game.environment.DepthAtmosphere;
import game.environment.EnvironmentManager;
import game.gui.util.Button;
import game.gui.util.ButtonType;
import game.input.InputManager;
import game.input.RawInput;
import game.manager.GameScene;
import game.manager.GameState;
import game.manager.TextureManager;
import game.shader.PostFX;
import game.shadow.ShadowMap;
import game.saving.SaveManager;
import game.sounds.SoundManager;
import game.util.DisplayModes;
import game.util.FontRenderer;

import java.util.ArrayList;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

public final class OptionsMenu extends MenuScreen {
   private static boolean vsyncEnabled = false;

   protected OptionsMenu() {
      this.buttons.add(new Button("Ok", Display.getWidth() / 2 - 130, Display.getHeight() - 100, ButtonType.ACTION_BUTTON));
      this.buttons.add(new Button("Default Settings", Display.getWidth() / 2 + 130, Display.getHeight() - 100, ButtonType.ACTION_BUTTON));
      SaveManager.loadOptions();
      this.buttons.add(new Button(ButtonType.TAB, 0, "Controls"));
      this.buttons.add(new Button(ButtonType.TAB, 1, "Graphics"));
      this.buttons.add(new Button(ButtonType.TAB, 2, "Audio"));
      this.buttons.add(new Button(ButtonType.KEY_BINDING, 0, "Forward", this.findButton("Controls")));
      this.buttons.add(new Button(ButtonType.KEY_BINDING, 1, "Back", this.findButton("Controls")));
      this.buttons.add(new Button(ButtonType.KEY_BINDING, 2, "Left", this.findButton("Controls")));
      this.buttons.add(new Button(ButtonType.KEY_BINDING, 3, "Right", this.findButton("Controls")));
      this.buttons.add(new Button(ButtonType.KEY_BINDING, 4, "Jump / Go up", this.findButton("Controls")));
      this.buttons.add(new Button(ButtonType.KEY_BINDING, 5, "Go down", this.findButton("Controls")));
      this.buttons.add(new Button(ButtonType.KEY_BINDING, 6, "Interaction", this.findButton("Controls")));
      this.buttons.add(new Button(ButtonType.KEY_BINDING, 7, "Inventory", this.findButton("Controls")));
      this.buttons.add(new Button(ButtonType.KEY_BINDING, 8, "Map", this.findButton("Controls")));
      this.buttons.add(new Button(ButtonType.TOGGLE, 9, "Invert Mouse", this.findButton("Controls")));
      this.findButton("Invert Mouse").addOption("No");
      this.findButton("Invert Mouse").addOption("Yes");
      this.findButton("Invert Mouse").selectOption(getSettingValue("Invert Mouse"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 10, "Mouse Sensitivity", this.findButton("Controls")));
      this.findButton("Mouse Sensitivity").addOption("0.2");
      this.findButton("Mouse Sensitivity").addOption("0.6");
      this.findButton("Mouse Sensitivity").addOption("1.0");
      this.findButton("Mouse Sensitivity").addOption("1.4");
      this.findButton("Mouse Sensitivity").addOption("1.8");
      this.findButton("Mouse Sensitivity").addOption("2.2");
      this.findButton("Mouse Sensitivity").addOption("2.6");
      this.findButton("Mouse Sensitivity").addOption("3.0");
      this.findButton("Mouse Sensitivity").addOption("3.4");
      this.findButton("Mouse Sensitivity").addOption("3.8");
      this.findButton("Mouse Sensitivity").addOption("4.2");
      this.findButton("Mouse Sensitivity").addOption("4.6");
      this.findButton("Mouse Sensitivity").addOption("5.0");
      this.findButton("Mouse Sensitivity").selectOption(getSettingValue("Mouse Sensitivity"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 0, "Screen", this.findButton("Graphics")));
      this.findButton("Screen").addOption("Fullscreen");
      this.findButton("Screen").addOption("Windowed");
      this.findButton("Screen").addOption("Borderless");
      this.findButton("Screen").selectOption(getSettingValue("Screen"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 1, "Resolution", this.findButton("Graphics")));

      for (int i = 0; i < DisplayModes.getAvailableModes().size(); i++) {
         this.findButton("Resolution").addOption(DisplayModes.getAvailableModes().get(i).getWidth() + "x" + DisplayModes.getAvailableModes().get(i).getHeight());
      }

      this.findButton("Resolution").selectOption(getSettingValue("Resolution"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 2, "FPS", this.findButton("Graphics")));
      this.findButton("FPS").addOption("30");
      this.findButton("FPS").addOption("40");
      this.findButton("FPS").addOption("50");
      this.findButton("FPS").addOption("60");
      this.findButton("FPS").addOption("70");
      this.findButton("FPS").addOption("80");
      this.findButton("FPS").addOption("90");
      this.findButton("FPS").addOption("120");
      this.findButton("FPS").addOption("240");
      this.findButton("FPS").addOption("Unlimited");
      this.findButton("FPS").selectOption(getSettingValue("FPS"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 3, "Texture Quality", this.findButton("Graphics")));
      this.findButton("Texture Quality").addOption("Low");
      this.findButton("Texture Quality").addOption("Medium");
      this.findButton("Texture Quality").addOption("High");
      this.findButton("Texture Quality").selectOption(getSettingValue("Texture Quality"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 4, "Render Distance", this.findButton("Graphics")));
      this.findButton("Render Distance").addOption("Tiny");
      this.findButton("Render Distance").addOption("Short");
      this.findButton("Render Distance").addOption("Normal");
      this.findButton("Render Distance").addOption("High");
      this.findButton("Render Distance").addOption("Very High");
      this.findButton("Render Distance").addOption("Ultra");
      this.findButton("Render Distance").selectOption(getSettingValue("Render Distance"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 5, "Particle Quantity", this.findButton("Graphics")));
      this.findButton("Particle Quantity").addOption("None");
      this.findButton("Particle Quantity").addOption("Minimum");
      this.findButton("Particle Quantity").addOption("Standard");
      this.findButton("Particle Quantity").addOption("Maximum");
      this.findButton("Particle Quantity").selectOption(getSettingValue("Particle Quantity"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 6, "Caustics", this.findButton("Graphics")));
      this.findButton("Caustics").addOption("On");
      this.findButton("Caustics").addOption("Off");
      this.findButton("Caustics").selectOption(getSettingValue("Caustics"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 7, "Vsync", this.findButton("Graphics")));
      this.findButton("Vsync").addOption("On");
      this.findButton("Vsync").addOption("Off");
      this.findButton("Vsync").selectOption(getSettingValue("Vsync"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 8, "Antialiasing", this.findButton("Graphics")));
      this.findButton("Antialiasing").addOption("Off");
      this.findButton("Antialiasing").addOption("FXAA");
      this.findButton("Antialiasing").addOption("MSAA 4x");
      this.findButton("Antialiasing").addOption("FXAA + MSAA");
      this.findButton("Antialiasing").selectOption(getSettingValue("Antialiasing"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 9, "Shadow Quality", this.findButton("Graphics")));
      this.findButton("Shadow Quality").addOption("Off");
      this.findButton("Shadow Quality").addOption("Low");
      this.findButton("Shadow Quality").addOption("High");
      this.findButton("Shadow Quality").selectOption(getSettingValue("Shadow Quality"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 10, "Shadow Distance", this.findButton("Graphics")));
      this.findButton("Shadow Distance").addOption("Tiny");
      this.findButton("Shadow Distance").addOption("Short");
      this.findButton("Shadow Distance").addOption("Normal");
      this.findButton("Shadow Distance").addOption("High");
      this.findButton("Shadow Distance").addOption("Very High");
      this.findButton("Shadow Distance").addOption("Ultra");
      this.findButton("Shadow Distance").selectOption(getSettingValue("Shadow Distance"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 11, "SSAO", this.findButton("Graphics")));
      this.findButton("SSAO").addOption("Off");
      this.findButton("SSAO").addOption("Low");
      this.findButton("SSAO").addOption("High");
      this.findButton("SSAO").selectOption(getSettingValue("SSAO"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 12, "Motion Blur", this.findButton("Graphics")));
      this.findButton("Motion Blur").addOption("Off");
      this.findButton("Motion Blur").addOption("Low");
      this.findButton("Motion Blur").addOption("High");
      this.findButton("Motion Blur").selectOption(getSettingValue("Motion Blur"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 13, "Color Grading", this.findButton("Graphics")));
      this.findButton("Color Grading").addOption("Off");
      this.findButton("Color Grading").addOption("Subtle");
      this.findButton("Color Grading").addOption("Vivid");
      this.findButton("Color Grading").selectOption(getSettingValue("Color Grading"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 14, "Bloom", this.findButton("Graphics")));
      this.findButton("Bloom").addOption("On");
      this.findButton("Bloom").addOption("Off");
      this.findButton("Bloom").selectOption(getSettingValue("Bloom"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 15, "Vignette", this.findButton("Graphics")));
      this.findButton("Vignette").addOption("On");
      this.findButton("Vignette").addOption("Off");
      this.findButton("Vignette").selectOption(getSettingValue("Vignette"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 0, "Sound Effect", this.findButton("Audio")));
      this.findButton("Sound Effect").addOption("0%");
      this.findButton("Sound Effect").addOption("10%");
      this.findButton("Sound Effect").addOption("20%");
      this.findButton("Sound Effect").addOption("30%");
      this.findButton("Sound Effect").addOption("40%");
      this.findButton("Sound Effect").addOption("50%");
      this.findButton("Sound Effect").addOption("60%");
      this.findButton("Sound Effect").addOption("70%");
      this.findButton("Sound Effect").addOption("80%");
      this.findButton("Sound Effect").addOption("90%");
      this.findButton("Sound Effect").addOption("100%");
      this.findButton("Sound Effect").selectOption(getSettingValue("Sound Effect"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 1, "Ambient Sound", this.findButton("Audio")));
      this.findButton("Ambient Sound").addOption("0%");
      this.findButton("Ambient Sound").addOption("10%");
      this.findButton("Ambient Sound").addOption("20%");
      this.findButton("Ambient Sound").addOption("30%");
      this.findButton("Ambient Sound").addOption("40%");
      this.findButton("Ambient Sound").addOption("50%");
      this.findButton("Ambient Sound").addOption("60%");
      this.findButton("Ambient Sound").addOption("70%");
      this.findButton("Ambient Sound").addOption("80%");
      this.findButton("Ambient Sound").addOption("90%");
      this.findButton("Ambient Sound").addOption("100%");
      this.findButton("Ambient Sound").selectOption(getSettingValue("Ambient Sound"));
      this.buttons.add(new Button(ButtonType.TOGGLE, 2, "Music", this.findButton("Audio")));
      this.findButton("Music").addOption("0%");
      this.findButton("Music").addOption("10%");
      this.findButton("Music").addOption("20%");
      this.findButton("Music").addOption("30%");
      this.findButton("Music").addOption("40%");
      this.findButton("Music").addOption("50%");
      this.findButton("Music").addOption("60%");
      this.findButton("Music").addOption("70%");
      this.findButton("Music").addOption("80%");
      this.findButton("Music").addOption("90%");
      this.findButton("Music").addOption("100%");
      this.findButton("Music").selectOption(getSettingValue("Music"));
   }

   @Override
   public final void refreshLayout() {
      for (int i = 0; i < this.buttons.size(); i++) {
         this.buttons.get(i).layout();
      }

      this.buttons.set(0, new Button("Ok", Display.getWidth() / 2 - 130, Display.getHeight() - 100, ButtonType.ACTION_BUTTON));
      this.buttons.set(1, new Button("Default Settings", Display.getWidth() / 2 + 130, Display.getHeight() - 100, ButtonType.ACTION_BUTTON));
   }

   @Override
   protected final void draw() {
      Button.renderPanel();

      for (int i = 0; i < this.buttons.size(); i++) {
         this.buttons.get(i).render();
      }

      if (this.isExperimentalSetting()) {
         Button graphics = this.findButton("Graphics");
         GL11.glColor4f(1.0F, 0.35F, 0.3F, 1.0F);
         FontRenderer.drawCentered(
            graphics.getX() + graphics.getTextWidth() / 2,
            graphics.getY() - 18,
            "EXPERIMENTAL! Can change the experience of the game or drop performance!",
            0.5F
         );
         GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      }
   }

   /** Render distance above Normal, or shadow distance above High, is experimental. */
   private boolean isExperimentalSetting() {
      String render = this.findButton("Render Distance").getSelectedOption();
      String shadow = this.findButton("Shadow Distance").getSelectedOption();
      boolean renderHigh = render.equals("High") || render.equals("Very High") || render.equals("Ultra");
      boolean shadowHigh = shadow.equals("Very High") || shadow.equals("Ultra");
      return renderHigh || shadowHigh;
   }

   @Override
   protected final void onButtonClicked(Button button) {
      if (!button.hasLabel("Controls") && !button.hasLabel("Graphics") && !button.hasLabel("Audio")) {
         this.findButton("Forward").setSelected(false);
         this.findButton("Back").setSelected(false);
         this.findButton("Left").setSelected(false);
         this.findButton("Right").setSelected(false);
         this.findButton("Jump / Go up").setSelected(false);
         this.findButton("Go down").setSelected(false);
         this.findButton("Interaction").setSelected(false);
         this.findButton("Inventory").setSelected(false);
         this.findButton("Map").setSelected(false);
         this.findButton("Invert Mouse").setSelected(false);
         this.findButton("Mouse Sensitivity").setSelected(false);
         this.findButton("Resolution").setSelected(false);
         this.findButton("Screen").setSelected(false);
         this.findButton("FPS").setSelected(false);
         this.findButton("Texture Quality").setSelected(false);
         this.findButton("Render Distance").setSelected(false);
         this.findButton("Particle Quantity").setSelected(false);
         this.findButton("Caustics").setSelected(false);
         this.findButton("Vsync").setSelected(false);
         this.findButton("Antialiasing").setSelected(false);
         this.findButton("Shadow Quality").setSelected(false);
         this.findButton("Shadow Distance").setSelected(false);
         this.findButton("SSAO").setSelected(false);
         this.findButton("Motion Blur").setSelected(false);
         this.findButton("Color Grading").setSelected(false);
         this.findButton("Bloom").setSelected(false);
         this.findButton("Vignette").setSelected(false);
         this.findButton("Sound Effect").setSelected(false);
         this.findButton("Ambient Sound").setSelected(false);
         this.findButton("Music").setSelected(false);
      } else {
         this.findButton("Controls").setSelected(false);
         this.findButton("Graphics").setSelected(false);
         this.findButton("Audio").setSelected(false);
      }

      if (button.hasLabel("Ok")) {
         applySetting("Invert Mouse", this.findButton("Invert Mouse").getSelectedOption());
         applySetting("Mouse Sensitivity", this.findButton("Mouse Sensitivity").getSelectedOption());
         applySetting("Resolution", this.findButton("Resolution").getSelectedOption());
         applySetting("Screen", this.findButton("Screen").getSelectedOption());
         applySetting("FPS", this.findButton("FPS").getSelectedOption());
         applySetting("Texture Quality", this.findButton("Texture Quality").getSelectedOption());
         applySetting("Render Distance", this.findButton("Render Distance").getSelectedOption());
         applySetting("Particle Quantity", this.findButton("Particle Quantity").getSelectedOption());
         applySetting("Caustics", this.findButton("Caustics").getSelectedOption());
         applySetting("Vsync", this.findButton("Vsync").getSelectedOption());
         applySetting("Antialiasing", this.findButton("Antialiasing").getSelectedOption());
         applySetting("Shadow Quality", this.findButton("Shadow Quality").getSelectedOption());
         applySetting("Shadow Distance", this.findButton("Shadow Distance").getSelectedOption());
         applySetting("SSAO", this.findButton("SSAO").getSelectedOption());
         applySetting("Motion Blur", this.findButton("Motion Blur").getSelectedOption());
         applySetting("Color Grading", this.findButton("Color Grading").getSelectedOption());
         applySetting("Bloom", this.findButton("Bloom").getSelectedOption());
         applySetting("Vignette", this.findButton("Vignette").getSelectedOption());
         applySetting("Sound Effect", this.findButton("Sound Effect").getSelectedOption());
         applySetting("Ambient Sound", this.findButton("Ambient Sound").getSelectedOption());
         applySetting("Music", this.findButton("Music").getSelectedOption());
         this.syncSettingsToButtons();
         MenuController.currentMenuState = MenuController.prevMenuState;
      } else if (button.hasLabel("Default Settings")) {
         InputManager.init();
         applySetting("Screen", "Fullscreen");
         applySetting("Invert Mouse", "No");
         applySetting("Mouse Sensitivity", "1.0");
         applySetting("FPS", "50");
         applySetting("Render Distance", "Normal");
         applySetting("Particle Quantity", "Maximum");
         applySetting("Caustics", "Off");
         applySetting("Vsync", "Off");
         applySetting("Antialiasing", "Off");
         applySetting("Shadow Quality", "Off");
         applySetting("Shadow Distance", "Normal");
         applySetting("SSAO", "Off");
         applySetting("Motion Blur", "Off");
         applySetting("Color Grading", "Off");
         applySetting("Bloom", "On");
         applySetting("Vignette", "Off");
         applySetting("Sound Effect", "100%");
         applySetting("Ambient Sound", "100%");
         applySetting("Music", "100%");
         this.syncSettingsToButtons();
      } else {
         button.setSelected(true);
      }
   }

   private void syncSettingsToButtons() {
      this.findButton("Invert Mouse").selectOption(getSettingValue("Invert Mouse"));
      this.findButton("Mouse Sensitivity").selectOption(getSettingValue("Mouse Sensitivity"));
      this.findButton("Resolution").selectOption(getSettingValue("Resolution"));
      this.findButton("Screen").selectOption(getSettingValue("Screen"));
      this.findButton("FPS").selectOption(getSettingValue("FPS"));
      this.findButton("Texture Quality").selectOption(getSettingValue("Texture Quality"));
      this.findButton("Render Distance").selectOption(getSettingValue("Render Distance"));
      this.findButton("Particle Quantity").selectOption(getSettingValue("Particle Quantity"));
      this.findButton("Caustics").selectOption(getSettingValue("Caustics"));
      this.findButton("Vsync").selectOption(getSettingValue("Vsync"));
      this.findButton("Antialiasing").selectOption(getSettingValue("Antialiasing"));
      this.findButton("Shadow Quality").selectOption(getSettingValue("Shadow Quality"));
      this.findButton("Shadow Distance").selectOption(getSettingValue("Shadow Distance"));
      this.findButton("SSAO").selectOption(getSettingValue("SSAO"));
      this.findButton("Motion Blur").selectOption(getSettingValue("Motion Blur"));
      this.findButton("Color Grading").selectOption(getSettingValue("Color Grading"));
      this.findButton("Bloom").selectOption(getSettingValue("Bloom"));
      this.findButton("Vignette").selectOption(getSettingValue("Vignette"));
      this.findButton("Sound Effect").selectOption(getSettingValue("Sound Effect"));
      this.findButton("Ambient Sound").selectOption(getSettingValue("Ambient Sound"));
      this.findButton("Music").selectOption(getSettingValue("Music"));
      ArrayList<String> keys = new ArrayList<>();
      ArrayList<String> values = new ArrayList<>();
      keys.add("Forward");
      values.add(getSettingValue("Forward"));
      keys.add("Back");
      values.add(getSettingValue("Back"));
      keys.add("Left");
      values.add(getSettingValue("Left"));
      keys.add("Right");
      values.add(getSettingValue("Right"));
      keys.add("Jump / Go up");
      values.add(getSettingValue("Jump / Go up"));
      keys.add("Go down");
      values.add(getSettingValue("Go down"));
      keys.add("Interaction");
      values.add(getSettingValue("Interaction"));
      keys.add("Inventory");
      values.add(getSettingValue("Inventory"));
      keys.add("Map");
      values.add(getSettingValue("Map"));
      keys.add("Invert Mouse");
      values.add(getSettingValue("Invert Mouse"));
      keys.add("Mouse Sensitivity");
      values.add(getSettingValue("Mouse Sensitivity"));
      keys.add("Screen");
      values.add(getSettingValue("Screen"));
      keys.add("FPS");
      values.add(getSettingValue("FPS"));
      keys.add("Texture Quality");
      values.add(getSettingValue("Texture Quality"));
      keys.add("Render Distance");
      values.add(getSettingValue("Render Distance"));
      keys.add("Particle Quantity");
      values.add(getSettingValue("Particle Quantity"));
      keys.add("Caustics");
      values.add(getSettingValue("Caustics"));
      keys.add("Vsync");
      values.add(getSettingValue("Vsync"));
      keys.add("Antialiasing");
      values.add(getSettingValue("Antialiasing"));
      keys.add("Shadow Quality");
      values.add(getSettingValue("Shadow Quality"));
      keys.add("Shadow Distance");
      values.add(getSettingValue("Shadow Distance"));
      keys.add("SSAO");
      values.add(getSettingValue("SSAO"));
      keys.add("Motion Blur");
      values.add(getSettingValue("Motion Blur"));
      keys.add("Color Grading");
      values.add(getSettingValue("Color Grading"));
      keys.add("Bloom");
      values.add(getSettingValue("Bloom"));
      keys.add("Vignette");
      values.add(getSettingValue("Vignette"));
      keys.add("Sound Effect");
      values.add(getSettingValue("Sound Effect"));
      keys.add("Ambient Sound");
      values.add(getSettingValue("Ambient Sound"));
      keys.add("Music");
      values.add(getSettingValue("Music"));
      keys.add("Nickname");
      values.add(game.net.NetSession.nickname);
      SaveManager.saveOptions(keys, values);
      MenuController.refreshLayouts();
   }

   public static void applySetting(String setting, String value) {
      if (setting.equals("Nickname")) {
         game.net.NetSession.nickname = value;
      }

      if (setting.equals("Forward")) {
         InputManager.rebindKey("Forward", Integer.parseInt(value));
      }

      if (setting.equals("Back")) {
         InputManager.rebindKey("Back", Integer.parseInt(value));
      }

      if (setting.equals("Left")) {
         InputManager.rebindKey("Left", Integer.parseInt(value));
      }

      if (setting.equals("Right")) {
         InputManager.rebindKey("Right", Integer.parseInt(value));
      }

      if (setting.equals("Jump / Go up")) {
         InputManager.rebindKey("Jump / Go up", Integer.parseInt(value));
      }

      if (setting.equals("Go down")) {
         InputManager.rebindKey("Go down", Integer.parseInt(value));
      }

      if (setting.equals("Interaction")) {
         InputManager.rebindKey("Interaction", Integer.parseInt(value));
      }

      if (setting.equals("Inventory")) {
         InputManager.rebindKey("Inventory", Integer.parseInt(value));
      }

      if (setting.equals("Map")) {
         InputManager.rebindKey("Map", Integer.parseInt(value));
      }

      if (setting.equals("Invert Mouse")) {
         if (value.equals("Yes")) {
            RawInput.invertY = -1;
         }

         if (value.equals("No")) {
            RawInput.invertY = 1;
         }
      }

      if (setting.equals("Mouse Sensitivity")) {
         if (value.equals("0.2")) {
            RawInput.sensitivity = 0.2F;
         }

         if (value.equals("0.6")) {
            RawInput.sensitivity = 0.6F;
         }

         if (value.equals("1.0")) {
            RawInput.sensitivity = 1.0F;
         }

         if (value.equals("1.4")) {
            RawInput.sensitivity = 1.4F;
         }

         if (value.equals("1.8")) {
            RawInput.sensitivity = 1.8F;
         }

         if (value.equals("2.2")) {
            RawInput.sensitivity = 2.2F;
         }

         if (value.equals("2.6")) {
            RawInput.sensitivity = 2.6F;
         }

         if (value.equals("3.0")) {
            RawInput.sensitivity = 3.0F;
         }

         if (value.equals("3.4")) {
            RawInput.sensitivity = 3.4F;
         }

         if (value.equals("3.8")) {
            RawInput.sensitivity = 3.8F;
         }

         if (value.equals("4.2")) {
            RawInput.sensitivity = 4.2F;
         }

         if (value.equals("4.6")) {
            RawInput.sensitivity = 4.6F;
         }

         if (value.equals("5.0")) {
            RawInput.sensitivity = 5.0F;
         }
      }

      if (setting.equals("Resolution")) {
         DisplayModes.setModeByResolution(value);
      }

      if (setting.equals("Screen")) {
         DisplayModes.setScreenMode(value);
      }

      // Legacy key from before the option was renamed.
      if (setting.equals("Full Screen")) {
         DisplayModes.setScreenMode(value.equals("On") ? DisplayModes.SCREEN_FULLSCREEN : DisplayModes.SCREEN_WINDOWED);
      }

      if (setting.equals("FPS")) {
         if (value.equals("30")) {
            Main.targetFps = 30;
         } else if (value.equals("40")) {
            Main.targetFps = 40;
         } else if (value.equals("50")) {
            Main.targetFps = 50;
         } else if (value.equals("60")) {
            Main.targetFps = 60;
         } else if (value.equals("70")) {
            Main.targetFps = 70;
         } else if (value.equals("80")) {
            Main.targetFps = 80;
         } else if (value.equals("90")) {
            Main.targetFps = 90;
         } else if (value.equals("120")) {
            Main.targetFps = 120;
         } else if (value.equals("240")) {
            Main.targetFps = 240;
         } else if (value.equals("Unlimited")) {
            Main.targetFps = 0;
         }
      }

      if (setting.equals("Texture Quality")) {
         int resolution = TextureManager.getResolution();
         if (value.equals("High")) {
            resolution = 1;
         } else if (value.equals("Medium")) {
            resolution = 2;
         } else if (value.equals("Low")) {
            resolution = 4;
         }

         if (resolution != TextureManager.getResolution() && Main.getGameState() != GameState.STARTUP) {
            TextureManager.beginLoading(Main.getGameState());
            TextureManager.textureQuality = resolution;
            Main.gameState = GameState.RELOADING;
         }
      }

      if (setting.equals("Render Distance")) {
         if (value.equals("Tiny")) {
            DepthAtmosphere.visibilityFactor = 0.5F;
         } else if (value.equals("Short")) {
            DepthAtmosphere.visibilityFactor = 0.75F;
         } else if (value.equals("Normal")) {
            DepthAtmosphere.visibilityFactor = 1.0F;
         } else if (value.equals("High")) {
            DepthAtmosphere.visibilityFactor = 1.25F;
         } else if (value.equals("Very High")) {
            DepthAtmosphere.visibilityFactor = 1.5F;
         } else if (value.equals("Ultra")) {
            DepthAtmosphere.visibilityFactor = 2.0F;
         }
      }

      if (setting.equals("Vsync")) {
         if (value.equals("On")) {
            Display.setVSyncEnabled(true);
            vsyncEnabled = true;
         } else if (value.equals("Off")) {
            Display.setVSyncEnabled(false);
            vsyncEnabled = false;
         }
      }

      if (setting.equals("Antialiasing")) {
         if (value.equals("Off")) {
            PostFX.aaMode = PostFX.AA_OFF;
         } else if (value.equals("FXAA")) {
            PostFX.aaMode = PostFX.AA_FXAA;
         } else if (value.equals("MSAA 4x")) {
            PostFX.aaMode = PostFX.AA_MSAA;
         } else if (value.equals("FXAA + MSAA")) {
            PostFX.aaMode = PostFX.AA_BOTH;
         }
      }

      if (setting.equals("Shadow Quality")) {
         if (value.equals("Off")) {
            ShadowMap.shadowsEnabled = false;
         } else if (value.equals("High")) {
            ShadowMap.shadowsEnabled = true;
            ShadowMap.shadowQuality = 1;
            ShadowMap.shadowResolution = 2048;
         } else if (value.equals("Low")) {
            ShadowMap.shadowsEnabled = true;
            ShadowMap.shadowQuality = 2;
            ShadowMap.shadowResolution = 1024;
         }
      }

      if (setting.equals("Shadow Distance")) {
         if (value.equals("Tiny")) {
            ShadowMap.shadowDistanceFactor = 0.5F;
         } else if (value.equals("Short")) {
            ShadowMap.shadowDistanceFactor = 0.75F;
         } else if (value.equals("Normal")) {
            ShadowMap.shadowDistanceFactor = 1.0F;
         } else if (value.equals("High")) {
            ShadowMap.shadowDistanceFactor = 1.25F;
         } else if (value.equals("Very High")) {
            ShadowMap.shadowDistanceFactor = 1.5F;
         } else if (value.equals("Ultra")) {
            ShadowMap.shadowDistanceFactor = 2.0F;
         }
      }

      if (setting.equals("SSAO")) {
         if (value.equals("Off")) {
            PostFX.ssaoLevel = 0;
         } else if (value.equals("Low")) {
            PostFX.ssaoLevel = 1;
         } else if (value.equals("High")) {
            PostFX.ssaoLevel = 2;
         }
      }

      if (setting.equals("Motion Blur")) {
         if (value.equals("Off")) {
            PostFX.motionBlurLevel = 0;
         } else if (value.equals("Low")) {
            PostFX.motionBlurLevel = 1;
         } else if (value.equals("High")) {
            PostFX.motionBlurLevel = 2;
         }
      }

      if (setting.equals("Color Grading")) {
         if (value.equals("Off")) {
            PostFX.colorGradeLevel = 0;
         } else if (value.equals("Subtle")) {
            PostFX.colorGradeLevel = 1;
         } else if (value.equals("Vivid")) {
            PostFX.colorGradeLevel = 2;
         }
      }

      if (setting.equals("Bloom")) {
         PostFX.bloomLevel = value.equals("Off") ? 0 : 2;
      }

      if (setting.equals("Vignette")) {
         PostFX.vignetteEnabled = value.equals("On");
      }

      if (setting.equals("Caustics")) {
         if (value.equals("On")) {
            GameScene.causticEnabled = true;
         }

         if (value.equals("Off")) {
            GameScene.causticEnabled = false;
         }
      }

      if (setting.equals("Particle Quantity")) {
         if (value.equals("None")) {
            EnvironmentManager.particleCount = 0;
         } else if (value.equals("Minimum")) {
            EnvironmentManager.particleCount = 50;
         } else if (value.equals("Standard")) {
            EnvironmentManager.particleCount = 150;
         } else if (value.equals("Maximum")) {
            EnvironmentManager.particleCount = 300;
         }
      }

      if (setting.equals("Sound Effect")) {
         if (value.equals("0%")) {
            SoundManager.sfxVolume = 0.0F;
         } else if (value.equals("10%")) {
            SoundManager.sfxVolume = 0.1F;
         } else if (value.equals("20%")) {
            SoundManager.sfxVolume = 0.2F;
         } else if (value.equals("30%")) {
            SoundManager.sfxVolume = 0.3F;
         } else if (value.equals("40%")) {
            SoundManager.sfxVolume = 0.4F;
         } else if (value.equals("50%")) {
            SoundManager.sfxVolume = 0.5F;
         } else if (value.equals("60%")) {
            SoundManager.sfxVolume = 0.6F;
         } else if (value.equals("70%")) {
            SoundManager.sfxVolume = 0.7F;
         } else if (value.equals("80%")) {
            SoundManager.sfxVolume = 0.8F;
         } else if (value.equals("90%")) {
            SoundManager.sfxVolume = 0.9F;
         } else if (value.equals("100%")) {
            SoundManager.sfxVolume = 1.0F;
         }

         SoundManager.refreshVolumes();
      }

      if (setting.equals("Ambient Sound")) {
         if (value.equals("0%")) {
            SoundManager.ambientVolume = 0.0F;
         } else if (value.equals("10%")) {
            SoundManager.ambientVolume = 0.1F;
         } else if (value.equals("20%")) {
            SoundManager.ambientVolume = 0.2F;
         } else if (value.equals("30%")) {
            SoundManager.ambientVolume = 0.3F;
         } else if (value.equals("40%")) {
            SoundManager.ambientVolume = 0.4F;
         } else if (value.equals("50%")) {
            SoundManager.ambientVolume = 0.5F;
         } else if (value.equals("60%")) {
            SoundManager.ambientVolume = 0.6F;
         } else if (value.equals("70%")) {
            SoundManager.ambientVolume = 0.7F;
         } else if (value.equals("80%")) {
            SoundManager.ambientVolume = 0.8F;
         } else if (value.equals("90%")) {
            SoundManager.ambientVolume = 0.9F;
         } else if (value.equals("100%")) {
            SoundManager.ambientVolume = 1.0F;
         }

         SoundManager.refreshVolumes();
      }

      if (setting.equals("Music")) {
         if (value.equals("0%")) {
            SoundManager.musicVolume = 0.0F;
         } else if (value.equals("10%")) {
            SoundManager.musicVolume = 0.1F;
         } else if (value.equals("20%")) {
            SoundManager.musicVolume = 0.2F;
         } else if (value.equals("30%")) {
            SoundManager.musicVolume = 0.3F;
         } else if (value.equals("40%")) {
            SoundManager.musicVolume = 0.4F;
         } else if (value.equals("50%")) {
            SoundManager.musicVolume = 0.5F;
         } else if (value.equals("60%")) {
            SoundManager.musicVolume = 0.6F;
         } else if (value.equals("70%")) {
            SoundManager.musicVolume = 0.7F;
         } else if (value.equals("80%")) {
            SoundManager.musicVolume = 0.8F;
         } else if (value.equals("90%")) {
            SoundManager.musicVolume = 0.9F;
         } else if (value.equals("100%")) {
            SoundManager.musicVolume = 1.0F;
         }

         SoundManager.refreshVolumes();
      }
   }

   private static String getSettingValue(String setting) {
      if (setting.equals("Forward")) {
         return Integer.toString(InputManager.getKeyCode("Forward"));
      } else if (setting.equals("Back")) {
         return Integer.toString(InputManager.getKeyCode("Back"));
      } else if (setting.equals("Left")) {
         return Integer.toString(InputManager.getKeyCode("Left"));
      } else if (setting.equals("Right")) {
         return Integer.toString(InputManager.getKeyCode("Right"));
      } else if (setting.equals("Jump / Go up")) {
         return Integer.toString(InputManager.getKeyCode("Jump / Go up"));
      } else if (setting.equals("Go down")) {
         return Integer.toString(InputManager.getKeyCode("Go down"));
      } else if (setting.equals("Interaction")) {
         return Integer.toString(InputManager.getKeyCode("Interaction"));
      } else if (setting.equals("Inventory")) {
         return Integer.toString(InputManager.getKeyCode("Inventory"));
      } else if (setting.equals("Map")) {
         return Integer.toString(InputManager.getKeyCode("Map"));
      } else {
         if (setting.equals("Invert Mouse")) {
            if (RawInput.invertY == -1) {
               return "Yes";
            }

            if (RawInput.invertY == 1) {
               return "No";
            }
         }

         if (setting.equals("Mouse Sensitivity")) {
            return (int)RawInput.sensitivity + "." + ((int)(RawInput.sensitivity * 10.0F) - (int)RawInput.sensitivity * 10);
         } else if (setting.equals("Resolution")) {
            return DisplayModes.getCurrentMode().getWidth() + "x" + DisplayModes.getCurrentMode().getHeight();
         } else if (setting.equals("Screen")) {
            return DisplayModes.getScreenMode();
         } else if (setting.equals("FPS")) {
            return Main.targetFps > 0 ? "" + Main.targetFps : "Unlimited";
         } else {
            if (setting.equals("Texture Quality")) {
               if (TextureManager.getResolution() == 1) {
                  return "High";
               }

               if (TextureManager.getResolution() == 2) {
                  return "Medium";
               }

               if (TextureManager.getResolution() == 4) {
                  return "Low";
               }
            }

            if (setting.equals("Render Distance")) {
               if (DepthAtmosphere.visibilityFactor == 0.5F) {
                  return "Tiny";
               }

               if (DepthAtmosphere.visibilityFactor == 0.75F) {
                  return "Short";
               }

               if (DepthAtmosphere.visibilityFactor == 1.0F) {
                  return "Normal";
               }

               if (DepthAtmosphere.visibilityFactor == 1.25F) {
                  return "High";
               }

               if (DepthAtmosphere.visibilityFactor == 1.5F) {
                  return "Very High";
               }

               if (DepthAtmosphere.visibilityFactor == 2.0F) {
                  return "Ultra";
               }
            }

            if (setting.equals("Particle Quantity")) {
               if (EnvironmentManager.particleCount == 0) {
                  return "None";
               }

               if (EnvironmentManager.particleCount == 50) {
                  return "Minimum";
               }

               if (EnvironmentManager.particleCount == 150) {
                  return "Standard";
               }

               if (EnvironmentManager.particleCount == 300) {
                  return "Maximum";
               }
            }

            if (setting.equals("Caustics")) {
               if (GameScene.causticEnabled) {
                  return "On";
               }

               if (!GameScene.causticEnabled) {
                  return "Off";
               }
            }

            if (setting.equals("Vsync")) {
               if (vsyncEnabled) {
                  return "On";
               }

               if (!vsyncEnabled) {
                  return "Off";
               }
            }

            if (setting.equals("Antialiasing")) {
               if (PostFX.aaMode == PostFX.AA_OFF) {
                  return "Off";
               }

               if (PostFX.aaMode == PostFX.AA_FXAA) {
                  return "FXAA";
               }

               if (PostFX.aaMode == PostFX.AA_MSAA) {
                  return "MSAA 4x";
               }

               if (PostFX.aaMode == PostFX.AA_BOTH) {
                  return "FXAA + MSAA";
               }
            }

            if (setting.equals("Shadow Quality")) {
               if (!ShadowMap.shadowsEnabled) {
                  return "Off";
               }

               return ShadowMap.shadowQuality == 1 ? "High" : "Low";
            }

            if (setting.equals("Shadow Distance")) {
               if (ShadowMap.shadowDistanceFactor == 0.5F) {
                  return "Tiny";
               }

               if (ShadowMap.shadowDistanceFactor == 0.75F) {
                  return "Short";
               }

               if (ShadowMap.shadowDistanceFactor == 1.0F) {
                  return "Normal";
               }

               if (ShadowMap.shadowDistanceFactor == 1.25F) {
                  return "High";
               }

               if (ShadowMap.shadowDistanceFactor == 1.5F) {
                  return "Very High";
               }

               if (ShadowMap.shadowDistanceFactor == 2.0F) {
                  return "Ultra";
               }
            }

            if (setting.equals("SSAO")) {
               if (PostFX.ssaoLevel == 0) {
                  return "Off";
               }

               if (PostFX.ssaoLevel == 1) {
                  return "Low";
               }

               if (PostFX.ssaoLevel == 2) {
                  return "High";
               }
            }

            if (setting.equals("Motion Blur")) {
               if (PostFX.motionBlurLevel == 0) {
                  return "Off";
               }

               if (PostFX.motionBlurLevel == 1) {
                  return "Low";
               }

               if (PostFX.motionBlurLevel == 2) {
                  return "High";
               }
            }

            if (setting.equals("Color Grading")) {
               if (PostFX.colorGradeLevel == 0) {
                  return "Off";
               }

               if (PostFX.colorGradeLevel == 1) {
                  return "Subtle";
               }

               if (PostFX.colorGradeLevel == 2) {
                  return "Vivid";
               }
            }

            if (setting.equals("Bloom")) {
               return PostFX.bloomLevel == 0 ? "Off" : "On";
            }

            if (setting.equals("Vignette")) {
               return PostFX.vignetteEnabled ? "On" : "Off";
            }

            if (setting.equals("Sound Effect")) {
               return (int)(SoundManager.sfxVolume * 100.0F) + "%";
            } else if (setting.equals("Ambient Sound")) {
               return (int)(SoundManager.ambientVolume * 100.0F) + "%";
            } else {
               return setting.equals("Music") ? (int)(SoundManager.musicVolume * 100.0F) + "%" : "";
            }
         }
      }
   }
}
