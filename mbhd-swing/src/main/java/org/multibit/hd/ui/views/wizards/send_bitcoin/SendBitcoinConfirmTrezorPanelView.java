package org.multibit.hd.ui.views.wizards.send_bitcoin;

import com.google.common.base.Optional;
import com.google.common.eventbus.Subscribe;
import net.miginfocom.swing.MigLayout;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.params.MainNetParams;
import org.multibit.hd.core.config.BitcoinConfiguration;
import org.multibit.hd.core.config.Configurations;
import org.multibit.hd.core.config.LanguageConfiguration;
import org.multibit.hd.core.dto.SendRequestSummary;
import org.multibit.hd.core.services.BitcoinNetworkService;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.core.utils.BitcoinSymbol;
import org.multibit.hd.hardware.core.HardwareWalletService;
import org.multibit.hd.hardware.core.events.HardwareWalletEvent;
import org.multibit.hd.hardware.core.messages.ButtonRequest;
import org.multibit.hd.ui.events.view.ViewEvents;
import org.multibit.hd.ui.languages.Formats;
import org.multibit.hd.ui.languages.Languages;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.components.Labels;
import org.multibit.hd.ui.views.components.Panels;
import org.multibit.hd.ui.views.components.panels.PanelDecorator;
import org.multibit.hd.ui.views.fonts.AwesomeIcon;
import org.multibit.hd.ui.views.wizards.AbstractWizard;
import org.multibit.hd.ui.views.wizards.AbstractWizardPanelView;
import org.multibit.hd.ui.views.wizards.WizardButton;

import javax.swing.*;

/**
 * <p>Wizard to provide the following to UI:</p>
 * <ul>
 * <li>Ask the user to press ok on their Trezor whilst sending bitcoin</li>
 * </ul>
 *
 * @since 0.0.1
 *  
 */

public class SendBitcoinConfirmTrezorPanelView extends AbstractWizardPanelView<SendBitcoinWizardModel, SendBitcoinConfirmTrezorPanelModel> {

  JLabel detailsLabel;

  /**
   * @param wizard    The wizard managing the states
   * @param panelName The panel name to filter events from components
   */
  public SendBitcoinConfirmTrezorPanelView(AbstractWizard<SendBitcoinWizardModel> wizard, String panelName) {

    super(wizard, panelName, MessageKey.PRESS_CONFIRM_ON_TREZOR_TITLE, AwesomeIcon.SHIELD);


    HardwareWalletService.hardwareWalletEventBus.register(this);

  }

  @Override
  public void newPanelModel() {

  }

  @Override
  public void initialiseContent(JPanel contentPanel) {

    contentPanel.setLayout(new MigLayout(
            Panels.migXYLayout(),
            "[]", // Column constraints
            "[]10[]" // Row constraints
    ));

    detailsLabel = Labels.newBlankLabel();
    contentPanel.add(Labels.newPressConfirmOnTrezorNoteShort(), "wrap");
    contentPanel.add(detailsLabel);

  }

  @Override
  protected void initialiseButtons(AbstractWizard<SendBitcoinWizardModel> wizard) {

    PanelDecorator.addExitCancelNext(this, wizard);

  }

  @Override
  public void afterShow() {

  }

  @Override
  public void updateFromComponentModels(Optional componentModel) {

    // No need to update the wizard it has the references

  }

  /**
   * <p>Handle the hardware wallet events </p>
   *
   * @param event The hardware wallet event indicating a state change
   */
  @Subscribe
  public void onHardwareWalletEvent(final HardwareWalletEvent event) {

    log.debug("Received hardware event: '{}'.{}", event.getEventType().name(), event.getMessage());

    BitcoinNetworkService bitcoinNetworkService = CoreServices.getOrCreateBitcoinNetworkService();

    switch (event.getEventType()) {
      case SHOW_BUTTON_PRESS:
        // Update the transaction output count in the hardwareWalletService context
        Optional<Integer> transactionOutputCountOptional = CoreServices.getOrCreateHardwareWalletService().get().getContext().getTransactionOutputCount();
        int transactionOutputCount;
        if (transactionOutputCountOptional.isPresent()) {
          transactionOutputCount = transactionOutputCountOptional.get() + 1;
        } else {
          // Originally the count is absent - start counting at 0
          transactionOutputCount = 0;
        }
        CoreServices.getOrCreateHardwareWalletService().get().getContext().setTransactionOutputCount(Optional.of(transactionOutputCount));

        // Update label with descriptive text matching what the Trezor is showing
        ButtonRequest buttonRequest = (ButtonRequest) event.getMessage().get();
        // General message is nothing else can be derived
        String labelText = buttonRequest.getButtonRequestType().name() + ": " + buttonRequest.getButtonMessage() + " " + buttonRequest.toString();

        if (bitcoinNetworkService.getLastSendRequestSummaryOptional().isPresent() && bitcoinNetworkService.getLastWalletOptional().isPresent()) {
          Wallet wallet = bitcoinNetworkService.getLastWalletOptional().get();

          Configurations.currentConfiguration = Configurations.newDefaultConfiguration();
          BitcoinConfiguration bitcoinConfiguration = Configurations.currentConfiguration.getBitcoin();
          LanguageConfiguration languageConfiguration = Configurations.currentConfiguration.getLanguage();

          Optional<Transaction> currentTransactionOptional = CoreServices.getOrCreateHardwareWalletService().get().getContext().getTransaction();
          if (currentTransactionOptional.isPresent()) {
            Transaction currentTransaction = currentTransactionOptional.get();
            // Substitute mBTC for MICON
            String bitcoinSymbolText = bitcoinConfiguration.getBitcoinSymbol();
            if (BitcoinSymbol.MICON.toString().equals(bitcoinSymbolText)) {
              bitcoinSymbolText = BitcoinSymbol.MBTC.getSymbol();
            }
            switch (buttonRequest.getButtonRequestType()) {
              case CONFIRM_OUTPUT:
                if (transactionOutputCount >= currentTransaction.getOutputs().size()) {
                  log.debug("Seeing more button presses than there are tx outputs - using the general message");
                } else {
                  String[] transactionOutputAmount = Formats.formatCoinAsSymbolic(currentTransaction.getOutput(transactionOutputCount).getValue(), languageConfiguration, bitcoinConfiguration);

                  Address transactionOutputAddress = currentTransaction.getOutput(transactionOutputCount).getAddressFromP2PKHScript(MainNetParams.get());
                  labelText = Languages.safeText(MessageKey.TREZOR_TRANSACTION_OUTPUT_CONFIRM, new String[]{ transactionOutputAmount[0] + transactionOutputAmount[1] + " " + bitcoinSymbolText, transactionOutputAddress == null ? "" : transactionOutputAddress.toString()});
                }
                break;
              case SIGN_TX:
                // The Trezor device shows the amount including fee so work that out
                Coin transactionAmount = currentTransaction.getValue(wallet).negate().add(currentTransaction.getFee());
                String[] transactionAmountFormatted = Formats.formatCoinAsSymbolic(transactionAmount, languageConfiguration, bitcoinConfiguration);

                String[] feeAmount = Formats.formatCoinAsSymbolic(currentTransaction.getFee(), languageConfiguration, bitcoinConfiguration);

                labelText = Languages.safeText(MessageKey.TREZOR_SIGN_CONFIRM, new String[]{ transactionAmountFormatted[0] + transactionAmountFormatted[1] + " " + bitcoinSymbolText, feeAmount[0] + feeAmount[1] + " " + bitcoinSymbolText});
                    break;
              default:

            }
          }
        }

        updateDetailsLabel(labelText);

        break;

      case SHOW_DEVICE_FAILED:
      case SHOW_DEVICE_DETACHED:
      case SHOW_DEVICE_READY:
      case ADDRESS:
      case SHOW_PIN_ENTRY:
      case SHOW_OPERATION_FAILED:
      case PUBLIC_KEY:
        // Update label
        updateDetailsLabel(event.getEventType().name() + " " +
                event.getMessage().get().toString().subSequence(0, Math.min(event.getMessage().toString().length(), 60)));
        break;
      case SHOW_OPERATION_SUCCEEDED:
        SwingUtilities.invokeLater(
                new Runnable() {
                  @Override
                  public void run() {
                    // Enable next button
                    ViewEvents.fireWizardButtonEnabledEvent(
                            getPanelName(),
                            WizardButton.NEXT,
                            true
                    );

                    // The tx is now complete so commit and broadcast it
                    // Trezor will provide a signed serialized transaction
                    byte[] deviceTxPayload = CoreServices.getOrCreateHardwareWalletService().get().getContext().getSerializedTx().toByteArray();

                    log.info("DeviceTx payload:\n{}", Utils.HEX.encode(deviceTxPayload));

                    // Load deviceTx
                    Transaction deviceTx = new Transaction(MainNetParams.get(), deviceTxPayload);

                    log.info("deviceTx:\n{}", deviceTx.toString());

                    // Check the signatures are canonical
                    for (TransactionInput txInput : deviceTx.getInputs()) {
                      byte[] signature = txInput.getScriptSig().getChunks().get(0).data;
                      log.debug("Is signature canonical test result '{}' for txInput '{}', signature '{}'", TransactionSignature.isEncodingCanonical(signature), txInput.toString(), Utils.HEX.encode(signature));
                    }

                    log.debug("Committing and broadcasting the last tx");

                    BitcoinNetworkService bitcoinNetworkService = CoreServices.getOrCreateBitcoinNetworkService();

                    if (bitcoinNetworkService.getLastSendRequestSummaryOptional().isPresent() && bitcoinNetworkService.getLastWalletOptional().isPresent()) {
                      SendRequestSummary sendRequestSummary = bitcoinNetworkService.getLastSendRequestSummaryOptional().get();
                      // Substitute the signed tx from the trezor
                      log.debug("Substituting the Trezor signed tx '{}' for the unsigned version {}", deviceTx.toString(), sendRequestSummary.getSendRequest().get().tx.toString());
                      sendRequestSummary.getSendRequest().get().tx = deviceTx;
                      Wallet wallet = bitcoinNetworkService.getLastWalletOptional().get();

                      // Clear the previous remembered tx so that it is not committed twice
                      bitcoinNetworkService.setLastSendRequestSummaryOptional(Optional.<SendRequestSummary>absent());
                      bitcoinNetworkService.setLastWalletOptional(Optional.<Wallet>absent());

                      updateDetailsLabel("Transaction created");
                      bitcoinNetworkService.commitAndBroadcast(sendRequestSummary, wallet);

                    } else {
                      log.debug("Cannot commit and broadcast the last send as it is not present in bitcoinNetworkService");
                    }

                    // Move to the next panel
                    getWizardModel().showNext();
                  }
                });

        break;
    }
  }

  private void updateDetailsLabel(final String text) {
    SwingUtilities.invokeLater(
            new Runnable() {
              @Override
              public void run() {
                if (detailsLabel != null) {
                  detailsLabel.setText(text);
                }
              }
            });

  }
}