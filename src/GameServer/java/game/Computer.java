package game;

/**
Computer.java

Description: This class represents most of the data about a player, and handles loading/parsing/saving their XML file. It forks off incoming information
to other classes, such as ports, watches, and applications. It has the main threaded event loop that is 
used to process incoming operations from an individual player/someone interacting with their account.
This class is a beast, know it well lest ye be bitten.
*/

import assignments.DamageAssignment;
import assignments.LoginFailedAssignment;
import assignments.LoginSuccessAssignment;
import assignments.PacketAssignment;
import assignments.PacketNetwork;
import assignments.PacketPort;
import assignments.PacketWatch;
import java.util.*;

import com.hackwars.game.program.*;
import game.computer.dispatch.CommandDispatcher;
import game.computer.dispatch.CommandRegistry;
import game.computer.packet.ComputerDamagePacketBuilder;
import game.computer.packet.ComputerDamagePacketSnapshot;
import game.computer.packet.ComputerPacketBuilder;
import game.computer.packet.ComputerStandardPacketSnapshot;
import game.computer.packet.PortHealthSnapshot;
import game.computer.persistence.XmlComputerPersistence;
import game.computer.runtime.ComputerRuntimeCoordinator;
import game.computer.runtime.RuntimeCaptchaPayload;
import game.computer.runtime.RuntimeTickEventApplier;
import game.computer.runtime.RuntimeTickEventSink;
import game.computer.runtime.RuntimePortSnapshot;
import game.computer.runtime.RuntimeQueuedTask;
import game.computer.runtime.RuntimeTickState;
import game.computer.session.CaptchaChallenge;
import game.computer.session.ComputerSessionService;
import game.computer.session.LoginRequest;
import game.computer.session.PlayStatisticsRequest;
import game.computer.session.RemoteFunctionPackResult;
import org.w3c.dom.Node;
import java.util.concurrent.Semaphore;
import kotlin.jvm.functions.Function0;
import com.hackwars.game.functions.AddShowChoices;
import com.hackwars.game.functions.BankXP;
import com.hackwars.game.functions.ChangeWatchPort;
import com.hackwars.game.functions.CreateFolder;
import com.hackwars.game.functions.DeleteFolder;
import com.hackwars.game.functions.DeleteLogs;
import com.hackwars.game.functions.DoChallenge;
import com.hackwars.game.functions.Function;
import com.hackwars.game.functions.HttpXP;
import com.hackwars.game.functions.LaunchNetworkAttack;
import com.hackwars.game.functions.PortOnOff;
import com.hackwars.game.functions.RedirectXP;
import com.hackwars.game.functions.RepairXP;
import com.hackwars.game.functions.RequestAttackDefault;
import com.hackwars.game.functions.RequestFTPUpdate;
import com.hackwars.game.functions.RequestSave;
import com.hackwars.game.functions.RequestTask;
import com.hackwars.game.functions.RequestTrigger;
import com.hackwars.game.functions.RequestTriggerNote;
import com.hackwars.game.functions.RequestZombieAttack;
import com.hackwars.game.functions.SavePortNote;
import com.hackwars.game.functions.ScanXP;
import com.hackwars.game.functions.SetCode;
import com.hackwars.game.functions.SetDefaultPort;
import com.hackwars.game.functions.SetDummyPort;
import com.hackwars.game.functions.SetFTPPassword;
import com.hackwars.game.functions.UninstallPort;
import com.hackwars.game.functions.WatchXP;
import java.io.*;
import java.text.*;
import game.runchallenge.ChallengeRunner;
import hackscript.model.TypeBoolean;
import hackscript.model.TypeFloat;
import hackscript.model.TypeInteger;
import hackscript.model.TypeString;
import hackscript.model.Variable;
import view.Task;
import util.LoadXML;
import util.LocalWebConfig;
import util.Time;

public class Computer implements Runnable{//Runnable is an interface that allows us to make this class be a thread.
	private static String getPropertySafe(String key,String fallback){
		try{
			return System.getProperty(key,fallback);
		}catch(SecurityException e){
			return fallback;
		}
	}

	//max ops values.
	public static final int FREE_MAX_OPS = 4096;
	public static final int PAY_MAX_OPS = 16384;
	private static final boolean REMOTE_XMLRPC_ENABLED = Boolean.parseBoolean(getPropertySafe("hackwars.remoteXmlRpc", "false"));
	public int MAX_OPS = 4096;
	
	//file size limits.
	public static final int FREE_FILE_SIZE_LIMIT = 60000;
	public static final int PAY_FILE_SIZE_LIMIT = 240000;
	public int FILE_SIZE_LIMIT=60000;

    // makers (allows for selling)
    public final static HashMap makers = new HashMap();
    static {
        makers.put("Alexander", 15.0f);
        makers.put("Low", 30.0f);
        makers.put("Medium", 150.0f);
        makers.put("High", 1500.0f);
        makers.put("Rare", 15000.0f);
        makers.put("Holiday", 250.0f);
        makers.put("I", 15.0f);
        makers.put("II", 23.0f);
        makers.put("III", 36.0f);
        makers.put("IV", 56.0f);
        makers.put("V", 87.0f);
        makers.put("VI", 134.0f);
        makers.put("VII", 208.0f);
        makers.put("VIII", 322.0f);
        makers.put("IX", 500.0f);
        makers.put("X", 775.0f);
        makers.put("XI", 1201.0f);
        makers.put("XII", 1861.0f);
        makers.put("XIII", 2885.0f);
        makers.put("XIV", 4471.0f);
        makers.put("XV", 6930.0f);
        makers.put("XVI", 10742.0f);
        makers.put("XVII", 16649.0f);
        makers.put("XVIII", 25807.0f);
        makers.put("XIX", 40000.0f);
        makers.put("XX", 62000.0f);
        makers.put("Trash.I", 15.0f);
        makers.put("Trash.II", 17.0f);
        makers.put("Trash.III", 19.0f);
        makers.put("Trash.IV", 21.0f);
        makers.put("Trash.V", 24.0f);
        makers.put("Trash.VI", 27.0f);
        makers.put("Trash.VII", 30.0f);
        makers.put("Trash.VIII", 34.0f);
        makers.put("Trash.IX", 38.0f);
        makers.put("Trash.X", 43.0f);
        makers.put("Trash.XI", 49.0f);
        makers.put("Trash.XII", 55.0f);
        makers.put("Trash.XIII", 62.0f);
        makers.put("Trash.XIV", 69.0f);
        makers.put("Trash.XV", 78.0f);
        makers.put("Trash.XVI", 88.0f);
        makers.put("Trash.XVII", 99.0f);
        makers.put("Trash.XVIII", 111.0f);
        makers.put("Trash.XIX", 125.0f);
        makers.put("Trash.XX", 141.0f);
        makers.put("Trash.XXI", 158.0f);
        makers.put("Trash.XXII", 178.0f);
        makers.put("Trash.XXIII", 200.0f);
        makers.put("Trash.XXIV", 225.0f);
        makers.put("Trash.XXV", 253.0f);
        makers.put("Trash.XXVI", 285.0f);
        makers.put("Trash.XXVII", 321.0f);
        makers.put("Trash.XXVIII", 361.0f);
        makers.put("Trash.XXIX", 406.0f);
        makers.put("Trash.XXX", 457.0f);
        makers.put("Trash.XXXI", 514.0f);
        makers.put("Trash.XXXII", 578.0f);
        makers.put("Trash.XXXIII", 650.0f);
        makers.put("Trash.XXXIV", 731.0f);
        makers.put("Trash.XXXV", 823.0f);
        makers.put("Trash.XXXVI", 926.0f);
        makers.put("Trash.XXXVII", 1041.0f);
        makers.put("Trash.XXXVIII", 1171.0f);
        makers.put("Trash.XXXIX", 1318.0f);
        makers.put("Trash.XL", 1483.0f);
        makers.put("Token.Silverlight", 500.0f);
        makers.put("Token.Draconis", 250.0f);
        makers.put("Xyphex.I", 15.0f);
        makers.put("Xyphex.II", 23.0f);
        makers.put("Xyphex.III", 36.0f);
        makers.put("Xyphex.IV", 56.0f);
        makers.put("Xyphex.V", 87.0f);
        makers.put("Xyphex.VI", 134.0f);
        makers.put("Xyphex.VII", 208.0f);
        makers.put("Xyphex.VIII", 322.0f);
        makers.put("Xyphex.IX", 500.0f);
        makers.put("Xyphex.X", 775.0f);
        makers.put("Xyphex.XI", 1201.0f);
        makers.put("Xyphex.XII", 1861.0f);
        makers.put("Xyphex.XIII", 2885.0f);
        makers.put("Xyphex.XIV", 4471.0f);
        makers.put("Xyphex.XV", 6930.0f);
        makers.put("Xyphex.XVI", 10742.0f);
        makers.put("Xyphex.XVII", 16649.0f);
        makers.put("Xyphex.XVIII", 25807.0f);
        makers.put("Xyphex.XIX", 40000.0f);
        makers.put("Xyphex.XX", 62000.0f);
    }
	
	//packets that originate from the client.
	private final static HashMap clientPackets = new HashMap();
	static {
		clientPackets.put("fetchports",0);
		clientPackets.put("setdefaultport",0);
		clientPackets.put("changenetwork",0);
		clientPackets.put("healport",0);
		clientPackets.put("requestequipment",0);
		clientPackets.put("installequipment",0);
		clientPackets.put("repairequipment",1);
		clientPackets.put("fetchwatches",0);
		clientPackets.put("requestpage",1);
		clientPackets.put("requestpurchase",0);
		clientPackets.put("requesttrigger",0);
		clientPackets.put("requestsave",0);
		clientPackets.put("requesttask",0);
		clientPackets.put("requestwebpage",1);
		clientPackets.put("submit",0);
		clientPackets.put("makebounty",0);
		clientPackets.put("exit",0);
		clientPackets.put("vote",0);
		clientPackets.put("savepage",0);
		clientPackets.put("withdraw",0);
		clientPackets.put("requestdirectory",0);
		clientPackets.put("unlock",0);
		clientPackets.put("setftppassword",0);
		clientPackets.put("requestsecondarydirectory",0);
		clientPackets.put("requestcancelattack",0);
		clientPackets.put("cluedata",0);
		clientPackets.put("requestzombiecancelattack",0);
		clientPackets.put("installapplication",0);
		clientPackets.put("installwatch",0);
		clientPackets.put("setwatchobservedports",0);
		clientPackets.put("installfirewall",0);
		clientPackets.put("replaceapplication",0);
		clientPackets.put("uninstallport",0);
		clientPackets.put("portonoff",0);
		clientPackets.put("peekcode",0);
		clientPackets.put("peeklogs",0);
		clientPackets.put("saveportnote",0);
		clientPackets.put("setwatchquantity",0);
		clientPackets.put("setwatchonoff",0);
		clientPackets.put("setwatchnote",0);
		clientPackets.put("setwatchsearchfirewall",0);
		clientPackets.put("deletewatch",0);
		clientPackets.put("deletefirewall",0);
		clientPackets.put("changewatchport",0);
		clientPackets.put("changewatchtype",0);
		clientPackets.put("deletefolder",0);
		clientPackets.put("setdummyport",0);
		clientPackets.put("changedailypay",0);
		clientPackets.put("deletelogs",0);
		clientPackets.put("createfolder",0);
		clientPackets.put("put",0);
		clientPackets.put("get",0);
		clientPackets.put("malget",0);
		clientPackets.put("requestfile",0);
		clientPackets.put("requestgame",0);
		clientPackets.put("requestscan",1);
		clientPackets.put("savefile",0);
		clientPackets.put("compilefile",0);
		clientPackets.put("deletemulti",0);
		clientPackets.put("deletefile",0);
		clientPackets.put("setfiledescription",0);
		clientPackets.put("setfileprice",0);
		clientPackets.put("emptypettycash",0);
		clientPackets.put("finalizecancelled",0);
		clientPackets.put("requestattack",1);
		clientPackets.put("requestzombieattack",1);
		clientPackets.put("transfer",0);
		clientPackets.put("deposit",0);
		clientPackets.put("dochallenge",0);
		clientPackets.put("sellfile",0);
		clientPackets.put("sellfilemulti",0);
		clientPackets.put("decompilefile",0);
	}

	//heal limit
	public int HEAL_LIMIT = 9;
	
	//NOOB Safety.
	private int noobLevel=30;

	//SQL CONNECTION INFO.
	private String Connection="127.0.0.1";
	private String DB="hackerforum";
	private String Username="root";
	private String Password="";
	
	private String Connection2="127.0.0.1";
	private String DB2="hackwars_drupal";
	private String Username2="root";
	private String Password2="";
	static final boolean LOCAL_AUTH_FALLBACK=!"false".equalsIgnoreCase(getPropertySafe("hackwars.localAuthFallback","true"));

	EquipmentSheet MyEquipmentSheet=new EquipmentSheet(this);//Keeps track of equipment currently installed and other such things.
    NewFireWall MyNewFireWall = null; //new NewFireWall(); // because I hate static variables, cause they hate me.  Used to generate firewalls.

	int xpTable[]=new int[100];//Table of XP per level.
	static final long ATTACK_RATE=2000;//How frequently should an attack tack place.
	static final long CHANGE_NETWORKS=180000;//How often can the player change networks?

	long lastChangeNetwork=0;//Keep track of the last time the player changed networks.
	long lastAttack=0;//At what time did an attack last take place.
	long healCounter=0;//Used to decide how frequently a port should heal based on Mod.
	static final long PACKET_TIMEOUT=500;//How frequently should we generate a packet?
	long lastSent=0;//Last time that a packet was sent.
	static final long PING_TIMEOUT=20000; //how long should we wait after a ping to determine whether the player has closed the client or not.
	long lastPingTime = 0;
	long logInTime = 0;
	static final long CLIENT_PACKET_TIMEOUT = 600000;
	long lastClientPacketTime = 0;
	static final int CAPTCHA_COUNT = 200;
	
	final long COMPUTER_TIMEOUT=1400000-(int)(700000*Math.random());//How long before we re-write the computer to disk.
	//private static final long COMPUTER_TIMEOUT=60000;//-(int)(1000*Math.random());//How long before we re-write the computer to disk.

	static final long PAY_PERIOD=43200000;//How often should we be paid. (Per Day)
	final long AUTO_SAVE=1200000-(int)(600000*Math.random());//How often should we save the profile?	
	
	int type=0;//Is this an NPC or player?
	float dailyPaySize=1000;//How much do you make a day?
	float dailyPayReduction=1.0f;//Percent value that indicates how much daily pay should be reduced.
	float respawnMoney=0;//How much money does an NPC get when they respawn.
	float maximumPettyCash=0;//For some NPCs we want to limit the cash in their petty, so that they can't be robbed for tons.
	public static final int PLAYER=0;
	public static final int NPC=1;
	public boolean loggedIn = false; //whether the player has logged in, or was accessed.
	public boolean gateway=false;
	public boolean FileIO=true;
	public boolean upgradedAccount = false;
	boolean inactive = false;
	HashMap RecentQuestFinishers=new HashMap();//Players who've recently finished quests.

	long lastSave=0;//When was the last time that the profile was saved.
	boolean systemChange=true;//Has the system changed since we last sent a packet.
	boolean healthChange=true;//Should an update be given regarding the player's current port healths?
	boolean countDown=false;//Is a count down currently taking place?.
	static final long COUNTDOWN_LENGTH=180000;//How long should a countdown take?

	long countDownStart=0;//When did the countdown start?

	//Used to manage errors when the occur during load time.
	boolean LOAD_FAILURE=false;//The XML file Failed To Load.
	boolean LOGOUT=false;//Has a player requested that they be logged out.
	String errorMessage="";//An error message to report back to the player.
	String loadRequester="";//The IP of the individual who requested that this Computer be loaded.

	long lastAccessed=0;//When was the computer last accessed?
	static final long SLEEP_TIME=50;//How often can we process a remote call?
	long lastPaid=0;//When was the last time this player recieved their daily money.
	long overheatStart=-1;//Keep track of when an overheat started.
	public static final long OVER_HEAT_TIME=60000;//How long should an overheat take place for.

	Time MyTime=null;//Central time keeping thread.
	Thread MyThread=null;//The thread associated with this class.

	boolean GUI_READY=false;//This variable is used by the 3D chat to determine whether the GUI is in a state ready to start receiving walking packets.
	boolean Loading=false;//Is the computer currently loading.
	boolean Loaded=false;//Has the computer started loading.
	boolean run=true;//Used to set whether this computer's thread is running.
	boolean LOG_UPDATE=false;//Has the player's DB been updated?
	boolean locked=false;//Has the account been locked down?
	int lockCount=0;
	String unlockKey="";//What key will unlock the account.
	
	//Ports on the computer.
	HashMap Ports=new HashMap();
	
	//Information about Computer.
	String ip="";//IP Address of this computer.
	String userName;//Username associated with this computer.
	String password;//FTP password for this computer.
	int successfulHacks=0;//Number of successful hacks that this player has performed.
	String pageBody="";//Body of personal webpage.
	String pageTitle="";//Title of personal webpage.
	String adRevenueTarget="";//Target that daily pay should be placed in (may be malicious).
	String storeRevenueTarget="";//Target that daily store revenue should be placed in (may be malicious).
	String lastBountyHTTP="";//Keeps track of the last person to take over the daily pay of this computer.
		
	boolean pageChanged=false;//Has the page changed since last output to file system?
	int votes=0;//How many votes does the player currently have.
	int operationCount=0;//How many operations has a player performed since they last logged in?
	
	//Improved Network and Quest Functionality.
	HashMap CurrentQuests=new HashMap();
	ArrayList CompletedQuests=new ArrayList();
	ArrayList InvolvedQuests=new ArrayList();
	String network=Network.ROOT_NETWORK;//Keeps track of the network that this NPC is currently on.
	ArrayList AllowedNetworks=new ArrayList();//The networks a player is allowed access to.
    static final int DEDRICKS_QUEST = 10;
	
	FileSystem MyFileSystem=null;//The file system used for hack wars.
	MakeClue MyMakeClue=null;//The class for generating and checking clues.
	MakeBounty MyMakeBounty=null;//Used for handling bounties.
			
	//Current tasks that have been sent in for this computer to perform.
	final Semaphore available = new Semaphore(1, true);//Make it thread safe.
	ArrayList Tasks=new ArrayList();//The array of tasks.

	//Array of messages since last packet.
	ArrayList Messages=new ArrayList();
	//Array list of damage updates.
	ArrayList Damage=new ArrayList();
	
	//Array list of show choices requests from finalized attacks.
	ArrayList Choices=new ArrayList();
	
	//An instance of the central server used for communicating with client.
	HackerServerBridge MyHackerServer=null;
	int connectionID=-1;//ID of this client connection.
	PacketAssignment PA=new PacketAssignment(0);//The current packet assignment we're building.
	DamageAssignment DA=new DamageAssignment(0);//The current damage assignment we're building.

	//The parent Computer Handler that tasks can be dispatched to.
	NetworkSwitch MyComputerHandler=null;
	ComputerHandler RawComputerHandler=null;
	
	//Handle the watches installed on this computer.
	WatchHandler MyWatchHandler=null;

	//Player's experience in the various skills.
	public static final float CPU_CHART[]=new float[]{50.0f,100.0f,150.0f,200.0f,250.0f,300.0f,75.0f};//Maximum Loads of various CPUs.
	public static final float MEMORY_CHART[]=new float[]{8.0f,16.0f,24.0f,32.0f,8.0f};//Maximum Port Count.
	public static final int WATCH_CHART[]=new int[]{4,6,8,12,5};
	HashMap Stats=new HashMap();//Player statistics are stored in a hash map.
	ArrayList LogMessages=new ArrayList();//Allow players to save messages to their 'DB'.
	ArrayList Globals=new ArrayList();//Allow players to maintain global variables.
	
	int cputype=0;//What type of CPU is installed on this computer.
	int memorytype=0;//What type of Memory is installed on this computer.

	float pettyCash = 0.0f;//Money in petty cash.
	float bankMoney = 0.0f;//Money in bank.
	float currentCPU=0.0f;//The current CPU load.
	float reportCPU=0.0f;//The CPU load reported to the player.
	float baseCPU=0.0f;
	float currentWatchCost=0.0f;//The cost associated with the watches that are currently active.
	int myVotes=0;//How many votes do you have to use on websites you like.
	int voteCount=0;//How many times has your site been voted for.
	//The new commodity banks and pettys.
	String store="";
	public static final int Plutonium=4;
	public static final int YBCO=3;
	public static final int Silicon=2;
	public static final int Germanium=1;
	public static final int DuctTape=0;
	public static String commodityString[]=new String[]{"Duct Tape","Germanium","Silicon","YBCO","Plutonium"};
	public static int requiredRepairLevel[]=new int[]{0,15,45,75,90};
	public float repairXP[]=new float[]{15.0f,30.0f,60.0f,120.0f,240.0f};
	public static float commodityXP[]=new float[]{20.0f,40.0f,100.0f,400.0f,1000.0f};
	float commodityAmount[]=new float[]{0.0f,0.0f,0.0f,0.0f,0.0f};
	float commodityRespawn[]=new float[]{0.0f,0.0f,0.0f,0.0f,0.0f};
	
	//Default ports.
	int defaultBank=0;
	int defaultAttack=0;
	int defaultFTP=0;
	int defaultHTTP=0;
	int defaultShipping=0;
	static final int MAX_PORT=32;
	
	String profile=null;
	
	boolean repaired=false;
	
	//Drop Table info.
	DropTable MyDropTable=null;
	int dropTable=1;
	HackerFile lastDrop = null;
	
	//This hashmap contains all the virtual functions run within the run function.
	HashMap functions=null;
    
    // this hashmap contains the user's preferences
    HashMap preferences=null;
    boolean sendPreferences = false;
	
	//MessageHandler
	MessageHandler messageHandler = new MessageHandler(this);

	private final ComputerSessionService sessionService = new ComputerSessionService();
	private final XmlComputerPersistence xmlComputerPersistence = new XmlComputerPersistence();
	private final LegacyComputerPersistenceSupport persistenceSupport = new LegacyComputerPersistenceSupport(xmlComputerPersistence);
	private final ComputerLoadCoordinator loadCoordinator = new ComputerLoadCoordinator(sessionService,xmlComputerPersistence,persistenceSupport);
	private final ComputerPacketBuilder standardPacketBuilder = new ComputerPacketBuilder();
	private final ComputerDamagePacketBuilder damagePacketBuilder = new ComputerDamagePacketBuilder();
	private final ComputerRuntimeCoordinator runtimeCoordinator = new ComputerRuntimeCoordinator();
	private CommandDispatcher commandDispatcher = null;
	
	public boolean sentOverHeatedMessage = false;
	
	/**
	This function builds up the initial HashMap of functions.
	*/
	public void buildFunctionHash(){
		functions=new HashMap();
		functions.put("deletelogs",new DeleteLogs(this));
		functions.put("requestftpupdate",new RequestFTPUpdate(this));
		functions.put("requestzombieattack",new RequestZombieAttack(this));
		functions.put("requestattackdefault",new RequestAttackDefault(this));
		functions.put("addshowchoices",new AddShowChoices(this));
		functions.put("bankxp",new BankXP(this));
		functions.put("deletefolder",new DeleteFolder(this));
		functions.put("createfolder",new CreateFolder(this));
		functions.put("code",new SetCode(this));
		functions.put("dochallenge",new DoChallenge(this));
		functions.put("redirectxp",new RedirectXP(this));
		functions.put("repairxp",new RepairXP(this));
		functions.put("watchxp",new WatchXP(this));
		functions.put("httpxp",new HttpXP(this));
		functions.put("scanxp",new ScanXP(this));
		functions.put("setftppassword",new SetFTPPassword(this));
		functions.put("setdefaultport",new SetDefaultPort(this));
		functions.put("requesttrigger",new RequestTrigger(this));
		functions.put("requesttriggernote",new RequestTriggerNote(this));
		functions.put("requestsave",new RequestSave(this));
		functions.put("requesttask",new RequestTask(this));
		functions.put("setdummyport",new SetDummyPort(this));
		functions.put("portonoff",new PortOnOff(this));
		functions.put("saveportnote",new SavePortNote(this));
		functions.put("uninstallport",new UninstallPort(this));
		functions.put("changewatchport",new ChangeWatchPort(this));
		functions.put("launchNetworkAttack",new LaunchNetworkAttack(this));
		commandDispatcher = CommandRegistry.Companion.fromFunctions((Map<String, Function>) functions);
	}
	
	/**
	Return the current daily pay reduction value.
	*/
	public float getDailyPayReduction(){
		return(dailyPayReduction);
	}
	
	public void setDailyPayReduction(float dailyPayReduction){
		this.dailyPayReduction=dailyPayReduction;
	}
			
	/**
	Returns whether or not this account is an NPC.
	*/
	public boolean isNPC(){
		if(type==NPC){
			return(true);
		}
		return(false);
	}
	
	/**
	Add recent quest finishes.
	*/
	public void addRecentQuestFinisher(String ip){
		RecentQuestFinishers.put(ip,"true");
	}	
	
	/**
	Check whether a player has recently finished a quest.
	*/
	public boolean checkRecentQuestFinisher(String ip){
		if(RecentQuestFinishers.get(ip)==null)
			return(false);
		return(true);
	}
	
	/**
	Get the type of cpu currently installed on this computer.
	*/
	public int getCPUType(){
		return(cputype);
	}
	
	/**
	Get the number of votes that a player currently has.
	*/
	public int getVoteCount(){
		return(voteCount);
	}
	
	public void setVoteCount(int voteCount){
		this.voteCount=voteCount;
	}
	
	/**
	Get the noob safety protection.
	*/
	public int getNoobSafety(){
		return(noobLevel);
	}

	ComputerSessionService getSessionService(){
		return sessionService;
	}
	
	/**
	Get the array that is used to indicate the showChoices boxes that should be shown. 
	There may be more than one if multiple attacks are running, hence the array.
	*/
	public ArrayList getShowChoicesArray(){
		return(Choices);
	}
	
	/**
	Set the ad revenue target.
	*/
	public void setAdRevenueTarget(String adRevenueTarget){
		this.adRevenueTarget=adRevenueTarget;
	}
	
	/**
	Return the HashMap of quests a player is currently participating in.
	*/
	public HashMap getCurrentQuests(){
		return(CurrentQuests);
	}
	
	/**
	Get the current target of website revenue.
	*/
	public String getAdRevenueTarget(){
		return(adRevenueTarget);
	}
	
	/**
	Get the main packet being built up since the last time a packet was sent updating the client.
	*/
	public PacketAssignment getPacketAssignment(){
		return(PA);
	}
	
	/**
	Whether the computer is under attack or not
	*/
	public boolean isUnderAttack(){
		Object[] ports = Ports.values().toArray();
		for(int i=0;i<ports.length;i++){
			Port port = (Port)ports[i];
			String accessing = port.getAccessing();
			//System.out.println(port.getNumber()+": "+accessing);
			if(!accessing.equals("")){
				return(true);
			}		
		}
		return(false);
	}
	
	/**
	Get the maximum watches.
	*/
	public int getMaximumWatches(){
		return(WATCH_CHART[memorytype]+(int)MyEquipmentSheet.getWatchBonus());
	}
	
	public int getMaximumWatchesNoBonus(){
		return(WATCH_CHART[memorytype]);
	}
	
	/**
	Update a global variable.
	*/
	public void setGlobal(int index,Object data){
		if(index<20){
			Globals.set(index,data);
		}
	}
	
	/**
	Is this player a member?
	*/
	public boolean getFileIO(){
		return(FileIO);
	}
	
	/**
	Return a global variable.
	*/
	public Variable getGlobal(int index){
		if(index<20){
			return((Variable)Globals.get(index));
		}
		return(null);
	}
	
	/**
	Reset the player's logs.
	*/
	public void resetLogs(){
		LogMessages=new ArrayList();
		LogMessages.add(new String[]{"",""});
		LOG_UPDATE=true;
	}
	
	/**
	Return a string representation of the logs.
	*/
	public String getLogs(){
		if(LogMessages==null)
			return("");
		String returnMe="";
		for(int i=0;i<LogMessages.size();i++){
			String temp=((String[])LogMessages.get(i))[0];
			if(!temp.equals("null")){
				returnMe+=temp+"\n";
			}
		}
		return(returnMe);
	}
	
	/**
	Get the current store IP for the network the player is on.
	*/
	public String getStoreIP(){
		return(store);
	}
	
	/**
	Edit the logs associated with this account.
	*/
	public void editLogs(String data,String replace){
		replace=replace.replaceAll("\\\\", "\\\\\\\\");
		replace=replace.replaceAll("\\$", "\\\\\\$");
		data=HackerLinker.regexEscape(data);
	
		for(int i=0;i<LogMessages.size();i++){
			String content[]=(String[])LogMessages.get(i);
			if(content!=null){
				content[0]=content[0].replaceAll(data,replace);
				LogMessages.set(i,content);
			}
		}
		LOG_UPDATE=true;
		sendPacket();
	}
	
	/**
	Allow player's to save messages to their 'DB'.
	*/
	public void logMessage(String message,String ip,Long timestamp){
		Calendar c = Calendar.getInstance();
		if(timestamp!=-1){
			c.setTimeInMillis(timestamp);
		}
		int month=c.get(c.MONTH);
		String monthString="Dec";
		if(month==c.JANUARY)
			monthString="Jan";
		if(month==c.FEBRUARY)
			monthString="Feb";
		if(month==c.MARCH)
			monthString="Mar";
		if(month==c.APRIL)
			monthString="Apr";
		if(month==c.MAY)
			monthString="May";
		if(month==c.JUNE)
			monthString="Jun";
		if(month==c.JULY)
			monthString="Jul";
		if(month==c.AUGUST)
			monthString="Aug";
		if(month==c.SEPTEMBER)
			monthString="Sep";
		if(month==c.OCTOBER)
			monthString="Oct";
		if(month==c.NOVEMBER)
			monthString="Nov";
		if(month==c.DECEMBER)
			monthString="Dec";
			
		int dayOfMonth=c.get(c.DAY_OF_MONTH);
		int year=c.get(Calendar.YEAR);
		
		int hour=c.get(Calendar.HOUR);
		int minute=c.get(Calendar.MINUTE);
		int seconds=c.get(Calendar.SECOND);
		int ampm=c.get(Calendar.AM_PM);
		String pm="PM";
		String second;
		String minutes;
		if(seconds<10)
			second="0"+seconds;
		else
			second=""+seconds;
		if(minute<10)
			minutes="0"+minute;
		else
			minutes=""+minute;
		if(ampm==0)
			pm="AM";
			
		if(hour==0)
			hour=12;
			
		String stamp=dayOfMonth+"-"+monthString+"-"+year+" ("+hour+":"+minutes+":"+second+" "+pm+")";
		LogMessages.add(new String[]{stamp+" "+message,ip});
		if(LogMessages.size()>50)
			LogMessages.remove(0);
			
		LOG_UPDATE=true;
		sendPacket();
	}
	
	/**
	Deletes all the logs corresponding to a specific IP.
	*/
	public void deleteLogs(String ip){
		Iterator LogIterator=LogMessages.iterator();
		while(LogIterator.hasNext()){
			String[] S=(String[])LogIterator.next();
			if(S[1].equals(ip))
				LogIterator.remove();
		}
		
			try{
				Object[] params = new Object[]{ip,this.ip};
				sessionService.executeRemote("http://www.hackwars.net/xmlrpc/facebook.php","deleteLogs",params);
			}catch(Exception e){
				e.printStackTrace();
			}
		LOG_UPDATE=true;
		sendPacket();
	}
	
	/**
	Returns the IP of the last player ot take over this computer's HTTP as
	part of a bounty.
	*/
	public String getLastBountyHTTPIP(){
		return(lastBountyHTTP);
	}
	
	/**
	Return the equipment sheet for use by other aspects of the computer.
	*/
	public EquipmentSheet getEquipmentSheet(){
		return(MyEquipmentSheet);
	}
    
    public NewFireWall getNewFireWall() {
        return(MyNewFireWall);
    }
	
	/**
	Get whether or not the overheat timeout has finished.
	*/
	public long getOverheatStart(){
		return(overheatStart);
	}
	
	/**
	Set the player's hash.
	*/
	private String clientHash="";
	public void setClientHash(String clientHash){
		this.clientHash=clientHash;
	}
	String getClientHash(){
		return(clientHash);
	}
	
	/**
	Set the player's public key.
	*/
	public byte[] publicKey=null;
	public void setPublicKey(byte[] publicKey){
		this.publicKey=publicKey;
	}
	
	/**
	Respawn the NPC character.
	*/
	public void respawn(int HackType){
		if(type==NPC){
			if(MyDropTable==null)
				MyDropTable=new DropTable(dropTable,this);
		
			if(HackType==Port.BANKING){
				Random rgen = new Random();
				float respawnAmount = respawnMoney + rgen.nextFloat()*(2*(respawnMoney*0.25f)) - (respawnMoney*0.25f);
				pettyCash = respawnAmount;
				//pettyCash=respawnMoney;
			}else if(HackType==Port.FTP){
				HackerFile HF=MyDropTable.generateDrop();
				Object Parameter[]=new Object[]{"Public/",HF};
				HF.setLocation("Public/");
				MyComputerHandler.addData(new ApplicationData("savefile",Parameter,0,ip),ip);
			}
		}
	}
	
	/**
	Return the last dropped file
	*/
	public HackerFile getDrop(){
		return(lastDrop);
	}
	
	/**
	set the last dropped file
	*/
	public void setDrop(HackerFile file){
		lastDrop = file;
	}
	
	/**
	returns the drop table associated with this Computer.
	*/
	public DropTable getDropTable(){
	if(MyDropTable==null)
				MyDropTable=new DropTable(dropTable,this);
		return(MyDropTable);
	}
	
	/**
	Return the computer handler associated with this computer.
	*/
	public NetworkSwitch getComputerHandler(){
		return(MyComputerHandler);
	}
	
	/**
	Get the type of computer (NPC, or Player)
	*/
	public int getType(){
		return(type);
	}
	
	/**
	Start count down.
	*/
	public void startCountDown(){
		systemChange=true;
		countDown=true;
		countDownStart=MyTime.getCurrentTime();
	}
	
	/**
	Get the current time.
	*/
	public long getCurrentTime(){
		return(MyTime.getCurrentTime());
	}
	
	/**
	Get the server id associated with this computer.
	*/
	public String getServerID(){
		return(MyHackerServer.getServerID());
	}
	
	/**
	Get the watch level of the player.
	*/
	public float getWatchLevel(){
		return(getLevel(getStatXP("Watch")));
	}
	
	/**
	Get the attack level of the player.
	*/
	public float getAttackLevel(){
		return(getLevel(getStatXP("Attack")));
	}
	
	/**
	Get the attack level of the player.
	*/
	public float getBankLevel(){
		return(getLevel(getStatXP("Bank")));
	}
	
	/**
	Get the attack level of the player.
	*/
	public float getScanningLevel(){
		return(getLevel(getStatXP("Scanning")));
	}
	
	/**
	Get the attack level of the player.
	*/
	public float getFireWallLevel(){
		return(getLevel(getStatXP("FireWall")));
	}
		
	/**
	Get the HTTP level of the player.
	*/
	public float getHTTPLevel(){
		return(getLevel(getStatXP("Webdesign")));
	}
	
	/**
	Get the Redirecting level of the player.
	*/
	public float getRedirectingLevel(){
		return(getLevel(getStatXP("Redirecting")));
	}
	
	/**
	Get the repair level of the player.
	*/
	public float getRepairLevel(){
		return(getLevel(getStatXP("Repair")));
	}

	private float getStatXP(String statName){
		Object value=Stats.get(statName);
		if(value instanceof Float)
			return((Float)value);
		if(value instanceof Number)
			return(((Number)value).floatValue());
		return(0.0f);
	}
	
	/**
	Return the HashMap that is used to store a player's XP in various skills.
	*/
	public HashMap getStats(){
		return(Stats);
	}
	
	/**
	Set the store revenue target.
	*/
	public void setStoreRevenueTarget(String storeRevenueTarget){	
		this.storeRevenueTarget=storeRevenueTarget;
	}
	
	/**
	Set the IP address of the individual requesting that this profile be loaded.
	*/
	public void setLoadRequester(String loadRequester){
		this.loadRequester=loadRequester;
	}
	
	/**
	Get the watch handler attached to this computer.
	*/
	public WatchHandler getWatchHandler(){
		return(MyWatchHandler);
	}
	
	/**
	Get the password associated with this account's public features.
	*/
	public String getPassword(){
		return(password);
	}
	
	public void setPassword(String password){
		this.password=password;
	}
			
	/**
	Destroy all the watches installed on the given port number.
	*/
	public void destroyWatches(int port){
		MyWatchHandler.destroyWatches(port);
	}
	
	/**
	Increment the number of successful hacks that a player has performed.
	*/
	public void incrementSuccessfulHacks(){
		successfulHacks++;
	}
	
	/**
	Get the HashMap of ports installed on this computer.
	*/
	public HashMap getPorts(){
		return(Ports);
	}
	
	/**
	Get the title of the player's webpage.
	*/
	public String getTitle(){
		return(pageTitle);
	}
	
	/**
	Get the body of the player's webpage.
	*/
	public String getBody(){
		return(pageBody);
	}
	
	/**
	Tell the Computer to send a packet.
	*/
	public void sendPacket(){
		systemChange=true;
	}
	
	/**
	Send a damage packet.
	*/
	public void sendDamagePacket(){
		healthChange=true;
	}
	
	/**
	Get/Set whether or not a piece of hardware has been repaired during this session.
	*/
	public void setRepaired(boolean repaired){
		this.repaired=repaired;
	}
	
	public boolean getRepaired(){
		return(repaired);
	}

	/**
	Get the default bank associated with this computer.
	*/
	public int getDefaultBank(){
		return getPortOn(defaultBank, Port.BANKING);
	}
	
	/**
	Return a port that is On if the default happens to be off.
	*/
	public int getPortOn(int defaultPort, int portType) {
	
		//First use the default port value
		Port checkPort = (Port)Ports.get(defaultPort);
				
		if(checkPort != null) {
			if(checkPort.getType() == portType && checkPort.getOn())
				return defaultPort;
		}
				
		Iterator PortIterator=Ports.entrySet().iterator();
		int ii=0;
		while(PortIterator.hasNext()){
			Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
			if(TempPort.getType()==portType&&TempPort.getOn()&&!TempPort.getDummy()) {
				return TempPort.getNumber();
			}
			ii++;
		}
		
		return 0;
	}
	
	public void setDefaultBank(int defaultBank){
		this.defaultBank=defaultBank;
	}
	
	/**
	Get the default attack port associated with this computer.
	*/
	public int getDefaultAttack(){
		return getPortOn(defaultAttack, Port.ATTACK);
	}
	
	public void setDefaultAttack(int defaultAttack){
		this.defaultAttack=defaultAttack;
	}
	
	/**
	Return the default port used for shipping commodities.
	*/
	public int getDefaultShipping(){
		return getPortOn(defaultShipping, Port.SHIPPING);
	}
	
	public void setDefaultShipping(int defaultShipping){
		this.defaultShipping=defaultShipping;
	}
	
	/**
	Get the default HTTP port associated with this computer.
	*/
	public int getDefaultHTTP(){
		return getPortOn(defaultHTTP, Port.HTTP);
	}
	
	public void setDefaultHTTP(int defaultHTTP){
		this.defaultHTTP=defaultHTTP;
	}
	
	
	/**
	Get the default FTP port associated with this computer.
	*/
	public int getDefaultFTP(){
		return getPortOn(defaultFTP, Port.FTP);
	}
	
	public void setDefaultFTP(int defaultFTP){
		this.defaultFTP=defaultFTP;
	}
	
	/**
	Add a message to be dispatched to the client.
	*/
	public void addMessage(String Message){
		if(connectionID!=-1){
			//Messages.add(Message);
			messageHandler.addMessage(new Object[]{Message, messageHandler.GAME_MESSAGE},null);
			systemChange=true;
		}
	}	
	
	public void addMessage(Object[] Message){
		if(connectionID!=-1){
			//Messages.add(Message);
			messageHandler.addMessage(Message,null);
			systemChange=true;
		}
	}
	
	public void addMessage(String Message,Object[] parameters){
		if(connectionID!=-1){
			//Messages.add(Message);
			messageHandler.addMessage(new Object[]{Message, messageHandler.GAME_MESSAGE},parameters);
			systemChange=true;
		}
	}	
	
	public void addMessage(Object[] Message,Object[] parameters){
		if(connectionID!=-1){
			//Messages.add(Message);
			messageHandler.addMessage(Message,parameters);
			systemChange=true;
		}
	}	
	
	public void addMessage(Object[] Message,Object[] parameters,Object[] portInfo){
		if(connectionID!=-1){
			//Messages.add(Message);
			messageHandler.addMessage(Message,parameters,portInfo);
			systemChange=true;
		}
	}
	
	public ArrayList getMessages(){
		return(Messages);
	}

	/**
	Returns the amount of money in the Player's petty cash.
	This is the money that can be stollen.
	*/
	public float getPettyCash(){
		return(pettyCash);
	}
	
	
	/**
	Gets the current amount of a given commodity in this computers
	commodity stores.
	*/
	public float getCommodity(int commodityType){
		return(commodityAmount[commodityType]);
	}
	
	/**
	Respawn the commodity.
	*/
	public void respawnCommodity(int commodityType){
		if(type==NPC&&commodityAmount[commodityType]<=0.0)
			commodityAmount[commodityType]=commodityRespawn[commodityType];
	}
	
	/**
	Sets the amount of the given commodity.
	*/
	public void setCommodityAmount(int commodityType,float amount){
		this.commodityAmount[commodityType]=amount;
		
		if(type==NPC&&amount<=0){
			this.commodityAmount[commodityType]=commodityRespawn[commodityType];
		}
		
		sendPacket();
	}
	
	/**
	Set the amount of money in the player's petty cash.
	*/
	public void setPettyCash(float pettyCash){
		this.pettyCash=pettyCash;
		if(maximumPettyCash>0&&this.pettyCash>maximumPettyCash)
			this.pettyCash=maximumPettyCash;
		if(pettyCash<0)
			pettyCash=0;
	}
	
	/**
	Returns the amount of money in the Player's bank.
	This is the money that can be stollen.
	*/
	public float getBank(){
		return(bankMoney);
	}
	
	/**
	Set the amount of money in the player's bank.
	*/
	public void setBank(float bankMoney){
		this.bankMoney=bankMoney;
	}
	
	/**
	Return the maximum CPU load based on the current CPU installed.
	*/
	public float getMaximumCPULoad(){
		return(CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus());
	}
	
	public float getMaximumCPUNoBonus(){
		return(CPU_CHART[cputype]);
	}
	
	/**
	Returns whether or not this is a gateway NPC.
	*/
	public boolean isGateway(){
		return(gateway);
	}
	
	/**
	Get the network that this NPC is currently on.
	*/
	public String getNetwork(){
		return(network);
	}
	
	/**
	Return the amount of money in the player's bank.
	This money cannot be hacked.
	*/
	public float getBankMoney(){
		return(bankMoney);
	}
	
	/**
	Get the clue level of this computer.
	*/
	public int getClueLevel(){
		return(0);
	//	return(clueLevel);
	}
	
	/**
	Return the current CPU load of this computer.
	*/
	public float getCPULoad(){
		return(reportCPU);
	}
	
	/**
	Return the base CPU load before overheating is taken into account.
	*/
	public float getBaseCPULoad(){
		return(baseCPU);
	}
	
	/**
	Return the file system object.
	*/
	public FileSystem getFileSystem(){
		return(MyFileSystem);
	}
	
	/**
	Get the base amount of damage this player currently deals.
        Type = "Attack" or "Redirecting"
	*/
	public float getDamage(String type){
		float damage=2.0f;
		float attackXP=getStatXP(type);
		int i=0;
		try {
			while( ((int)attackXP > xpTable[i]) && i < 99) {
				i++;
			}
		} catch(ArrayIndexOutOfBoundsException e) {}
		//damage+=(float)((i+1)/5);
        damage += (float) ((i+1) * 0.2f);
		return(damage);
	}
	
	/**
	Get the current level of a stat based on the current XP.
	*/
	public int getLevel(float xp){
		int i=0;
		try{
			while(((int)xp>xpTable[i])&&i<99){
				i++;
			}
		}catch(ArrayIndexOutOfBoundsException e){}
		return(i+1);
	}
	
	/**
	Set whether this computer's thread should currently be running.
	*/
	public void setRun(boolean run){
		Network.getInstance(MyComputerHandler).removeFromNetwork(network,ip);//Remove the player from their current network.
		this.run=run;
		Thread thread = MyThread;
		if(thread!=null){
			thread.interrupt();
		}
		MyThread=null;
	}
	
	/**
	Get the class for checking/generating clues.
	*/
	public MakeClue getMakeClue(){
		return(MyMakeClue);
	}
	
	public ArrayList getDamage(){
		return(Damage);
	}
	
	public void returnPlayer(String profile){
		this.profile=profile;
	}
	
	/**
	Constructor.
	*/
	public Computer(String ip,ComputerHandler MyComputerHandler,Time MyTime,int connectionID,HackerServerBridge MyHackerServer){
		for(int i=0;i<20;i++)
			Globals.add(null);
	
		MyFileSystem=new FileSystem(this);//The virtual file sytem.
	//	MyMakeClue=new MakeClue(MyFileSystem,this,clueLevel);//The clue generator/checker.
		MyMakeBounty=new MakeBounty(MyFileSystem);//Used for generating bounties.
	
		//Create the experience table.
		int xp=83;
		int xpDiff=83;
		for(int i=0;i<100;i++){
			xpTable[i]=xp;
			xpDiff+=xpDiff/9.525;
			xp+=xpDiff;
		}
		
		this.MyComputerHandler=new NetworkSwitch(this,MyComputerHandler);
		this.RawComputerHandler=MyComputerHandler;
        this.MyNewFireWall = new NewFireWall(this.MyComputerHandler);
		this.ip=ip;
		this.MyTime=MyTime;
		this.connectionID=connectionID;
		this.MyHackerServer=MyHackerServer;
		
		MyWatchHandler=new WatchHandler(MyComputerHandler,this);
		
		while(lastAccessed==0)
			this.lastAccessed=MyTime.getCurrentTime();
		MyThread = new Thread(this,"Computer - "+ip);
		MyThread.start();
	}
	
	/**
	Constructor.
	*/
	public Computer(String userName,String ip,ComputerHandler MyComputerHandler,Time MyTime,int connectionID,HackerServerBridge MyHackerServer,boolean playerLogin){
		for(int i=0;i<20;i++)
			Globals.add(null);
	
		MyFileSystem=new FileSystem(this);//The virtual file sytem.
	//	MyMakeClue=new MakeClue(MyFileSystem,this,clueLevel);//The clue generator/checker.
		MyMakeBounty=new MakeBounty(MyFileSystem);//Used for generating bounties.

		this.ip=ip;
	
		//Create the experience table.
		int xp=83;
		int xpDiff=83;
		for(int i=0;i<100;i++){
			xpTable[i]=xp;
			xpDiff+=xpDiff/9.525;
			xp+=xpDiff;
		}
		
		this.MyComputerHandler=new NetworkSwitch(this,MyComputerHandler);
		this.RawComputerHandler=MyComputerHandler;
		
		this.userName=userName;
		this.MyTime=MyTime;
		this.connectionID=connectionID;
		this.MyHackerServer=MyHackerServer;
		
		MyWatchHandler=new WatchHandler(MyComputerHandler,this);
		
		while(lastAccessed==0)
			this.lastAccessed=MyTime.getCurrentTime();
		MyThread = new Thread(this,"Computer - "+ip);
		MyThread.start();
	}

	
	/**
	Set the connection ID associated with this computer.
	If a player is already logged in and reconnects this is necessary.
	*/
	boolean RESEND_CAPTCHA=false;
	public void setConnectionID(int connectionID,String loginPassword){
		try{
			available.acquire();
			RESEND_CAPTCHA=true;
			this.lastAccessed=MyTime.getCurrentTime();
			loadRequester="";
			Tasks.add(0,new setConnectionIDTask(this,connectionID,crypt(loginPassword.getBytes(),clientHash)));
			available.release();
			
					if(REMOTE_XMLRPC_ENABLED){
						try{
							sessionService.requestFunctionPacks(ip);
						}catch(Exception e){
							e.printStackTrace();
						}
				}
				FileIO=true;
		}catch(Exception e){
			available.release();
			e.printStackTrace();	
		}
	}

	public void setConnectionID(int connectionID){
		try{
			available.acquire();
			RESEND_CAPTCHA=true;
			this.lastAccessed=MyTime.getCurrentTime();
			loadRequester="";
			Tasks.add(0,new setConnectionIDTask(this,connectionID,null));
			available.release();
			FileIO=true;
		}catch(Exception e){
			available.release();
			e.printStackTrace();
		}
	}
	
	/**
	Used to encrypt and decrypt XOR encrypted data.
	*/
	static public String crypt (byte [] data,String key)
	{
		for (int ii = 0; ii < data.length;ii++) {
			data [ii] ^= (int)(key.getBytes())[ii%key.length()]; 
		}
		return(new String(data));
	}
	
	/**
	Set a connection ID in a threaded environment.
	*/
	private class setConnectionIDTask implements Task{
		private int connectionID=-1;
		private String loginPassword="";
		private Computer MyComputer=null;
		
		public setConnectionIDTask(Computer MyComputer,int connectionID,String loginPassword){
			this.connectionID=connectionID;
			this.loginPassword=loginPassword;
			this.MyComputer=MyComputer;
		}
		
		public void execute(){
			if(loginPassword!=null){
				MyComputer.loginPassword=loginPassword;
			}
			if(checkLogin()){
				MyComputer.connectionID=connectionID;//The ID used to relay data back to the client-side.
				
				MyHackerServer.removeRandomKey(ip);
				Object O[]=MyHackerServer.getRandomKey(ip,clientHash,publicKey);
				LoginSuccessAssignment MyLoginSuccessAssignment=new LoginSuccessAssignment(0,ip,(String)O[0],isNPC());
				MyLoginSuccessAssignment.setPublicKey((byte[])O[1]);
				O=new Object[]{MyLoginSuccessAssignment,new Integer(connectionID)};
				MyHackerServer.addData(O);
				//Make sure we resend the network information.
				if(network!=null){
					Network.getInstance(MyComputerHandler).getNetworkInformation(network);
					PA.setPacketNetwork(Network.getInstance(MyComputerHandler).getNetworkInformation(network));
				}
				
				//Remove and re-add a player to the 3D chat.
				GUI_READY=false;//Don't allow packets to send since we can't trust the GUI is ready, the first packet we receive GUI side will set this to true.
				//WorldSingleton.getInstance().invalidatePlayer("game",ip);//Add the player into the 3D chat.
				//WorldSingleton.getInstance().addPlayer("game",ip,userName,npc);//Add the player into the 3D chat.
				
				//Force a packet update.
				systemChange=true;
				healthChange=true;
				LOG_UPDATE=true;
			}else{
				Object O[]=new Object[]{new LoginFailedAssignment(0),new Integer(connectionID)};
				MyHackerServer.addData(O);
			}
		}
	}
		
	/**
	getIP()
	returns the ip of the computer.
	**/
	public String getIP(){
		return(this.ip);
	}
	
	/**
	addData()
	Adds a function to be processed using a semaphore into the
	computer's processing stack.
	*/
	public void addData(Object MyApplicationData){
		try{
			available.acquire();
			if(Tasks.size()<50){
				operationCount+=1;//Keep track of how many operations have been peformed while this player is logged in.
				ApplicationData MAD=(ApplicationData)MyApplicationData;
				//We must make sure that the transactional data gets moved to the front of the list.
				boolean applicationData=false;
				if(MyApplicationData instanceof ApplicationData)
					applicationData=true;
				
				//if(!applicationData||!locked||!((ApplicationData)MyApplicationData).getSourceIP().equals(ip)||((ApplicationData)MyApplicationData).getSource()!=ApplicationData.OUTSIDE||connectionID==-1){
					boolean add = true;
                    if(locked){
						Object packet = clientPackets.get(MAD.getFunction());
						if(packet!=null){
							int count = (Integer)packet;
							if(count > 0){
								add = false;
							}
						}
					}
					if(add){
						if(MAD.getFunction().equals("bank")||MAD.getFunction().equals("pettycash"))
							Tasks.add(0,MyApplicationData);
						else
							Tasks.add(MyApplicationData);
					}
						
				//}
			}
			available.release();
		}catch(Exception e){
			e.printStackTrace();	
            available.release();
		}
	}
	
	/**
	Is the computer currently loading.
	*/
	public boolean getLoading(){
		return(Loading);
	}
	
	/**
	Is the computer completely loaded.
	*/
	public boolean getLoaded(){
		return(Loaded);
	}
	
	/**
	Set the password associated with a player.
	*/
	String loginPassword="";
	boolean playFabAuthenticated=false;
	public void setPlayFabAuthenticated(String userName){
		playFabAuthenticated=true;
		if(userName!=null&&userName.trim().length()>0){
			this.userName=userName;
		}
	}
	public void setLoginPassword(String loginPassword){
		this.loginPassword=crypt(loginPassword.getBytes(),clientHash);
	}
	
	/**
	private void checkLogin(String userName,String loginPassword){
		this.userName=userName;
		this.loginPassword=loginPassword;
		checkLogin();
	}*/
	
	/**
	Check to make sure that username and password are correct.
	*/
	public boolean checkLogin(){
		LoginRequest loginRequest = new LoginRequest(
			ip,
			userName,
			loginPassword,
			playFabAuthenticated,
			ServerRuntimeState.isTesting()
		);
		game.computer.session.LoginResult result = sessionService.authenticate(loginRequest);
		sendPreferences = result.getSendPreferences();
		return result.getAccepted();
	}
	
	/**
	Get the total level of a player.
	*/
	public int getTotalLevel(){
		int totalLevel=0;
		totalLevel+=getLevel(getStatXP("Attack"));
		totalLevel+=getLevel(getStatXP("Bank"));
		totalLevel+=getLevel(getStatXP("Watch"));
		totalLevel+=getLevel(getStatXP("Scanning"));
		totalLevel+=getLevel(getStatXP("FireWall"));
		totalLevel+=getLevel(getStatXP("Webdesign"));
		totalLevel+=getLevel(getStatXP("Redirecting"));
		totalLevel+=getLevel(getStatXP("Repair"));


		return(totalLevel);
	}
	
	/**
	Performs a challenge XML-RPC call.
	*/
	public void doChallengeRPC(String challengeID,String source){
	
		addMessage(MessageHandler.CHALLENGE_START,new Object[]{challengeID});
		addMessage("-----------------------");
		
		//Fetch the HackerFile associated with this ID.
		HackerFile ChallengeFile=null;
		ArrayList ChallengeFiles=MyFileSystem.getFilesOfType(HackerFile.CHALLENGE);
		HashMap Content=null;
		if(ChallengeFiles!=null)
		for(int i=0;i<ChallengeFiles.size();i++){
			HackerFile TempFile=(HackerFile)ChallengeFiles.get(i);
			Content=TempFile.getContent();
			String identifier=(String)Content.get("identifier");
			if(identifier!=null)
			if(challengeID.equals(identifier)){
				ChallengeFile=TempFile;
				break;
			}
		}
		
		if(ChallengeFile==null){//Did we find this file.
			addMessage(MessageHandler.CHALLENGE_NO_FILE);
			return;
		}
		
		String input=(String)Content.get("input");
		String output=(String)Content.get("output");
		String inputMultiple[]=input.split("&");
		String outputMultiple[]=output.split("&");
		String inputtype=(String)Content.get("inputtype");
		String outputtype=(String)Content.get("outputtype");
		int QuestID=new Integer((String)Content.get("questid"));
		String TaskName=(String)Content.get("task");
		
		boolean success=true;
		HashMap result=null;
		for(int ii=0;ii<inputMultiple.length;ii++){
			addMessage(MessageHandler.CHALLENGE_RUNNING_ATTEMPT,new Object[]{(ii+1)});
				
			try{	
				input=inputMultiple[ii];
				output=outputMultiple[ii];
						
				String[] sendStringInput=new String[0];
				Double[] sendDoubleInput=new Double[0];
				Integer[] sendIntegerInput=new Integer[0];
				String[] sendStringOutput=new String[0];
				Double[] sendDoubleOutput=new Double[0];
				Integer[] sendIntegerOutput=new Integer[0];
					
				
				if(inputtype.equals("String")){
					sendStringInput=input.split(",");
				}
				if(outputtype.equals("String")){
					sendStringOutput=output.split(",");
				}
				
					
				if(inputtype.equals("float")){
					String[] ss = input.split(",");
					sendDoubleInput = new Double[ss.length];
					for(int i=0;i<ss.length;i++){
						sendDoubleInput[i] = Double.parseDouble(ss[i]);
					}
				}
				if(outputtype.equals("float")){
					String[] ss = output.split(",");
					sendDoubleOutput = new Double[ss.length];
					for(int i=0;i<ss.length;i++){
						sendDoubleOutput[i] = Double.parseDouble(ss[i]);
					}
				}
					
				if(inputtype.equals("int")){
					String[] ss = input.split(",");
					sendIntegerInput = new Integer[ss.length];
					for(int i=0;i<ss.length;i++){
						sendIntegerInput[i] = Integer.parseInt(ss[i]);
					}
				}
				if(outputtype.equals("int")){
					String[] ss = output.split(",");
					sendIntegerOutput = new Integer[ss.length];
					for(int i=0;i<ss.length;i++){
						sendIntegerOutput[i] = Integer.parseInt(ss[i]);
					}
					
				}

				result=ChallengeRunner.getInstance().runToyProblem(source,new Integer(3000),sendDoubleInput,sendStringInput,sendIntegerInput,sendDoubleOutput,sendStringOutput,sendIntegerOutput);
									
				success = (boolean)(Boolean)result.get("success");
				
				Object[] stringOut = (Object[])result.get("outstring");
				Object[] doubleOut = (Object[])result.get("outdouble");
				Object[] intOut = (Object[])result.get("outint");
						
				for(int i=0;i<stringOut.length;i++){
					addMessage("String Outputted: "+stringOut[i]);
				}

				for(int i=0;i<doubleOut.length;i++){
					addMessage("Float Outputted: "+doubleOut[i]);
				}
				
				for(int i=0;i<intOut.length;i++){
					addMessage("Int Outputted: "+intOut[i]);
				}
				
				if(!success){
					addMessage((String)result.get("error"));
					break;
				}

			}catch(Exception e){
				e.printStackTrace();
			}
						
		}
		addMessage("-----------------------");
		
		//Reward.
		if(success){
			//We'd set a quest parameter to true here.
			String TaskLabel="";
			
			if(!checkQuest(QuestID)){//Make sure the quest isn't already complete.
				HashMap CurrentQuest=null;
				String label="";
				if(CurrentQuests.get(QuestID)!=null){
					CurrentQuest=(HashMap)((Object[])CurrentQuests.get(QuestID))[0];
					label=(String)((Object[])CurrentQuests.get(QuestID))[1];
				}
												
				if(CurrentQuest==null){
					CurrentQuest=new HashMap();
					CurrentQuest.put(TaskName,new Object[]{new Boolean(true),TaskLabel});
					CurrentQuests.put(QuestID,new Object[]{CurrentQuest,label});
				}else{
					CurrentQuest.put(TaskName,new Object[]{new Boolean(true),TaskLabel});
				}
			}
			
			addMessage(MessageHandler.CHALLENGE_COMPLETED);
		}else{
			addMessage(MessageHandler.CHALLENGE_FAILED);
		}
	}
	
	/**
	Tells the computer to begin loading player data in its thread.
	*/
	public void loadSave(){
		try{
			if(!Loading){
				available.acquire();
				Loading=true;
				Tasks.add(0,new loadSaveTask(this));
				available.release();
			}
		}catch(Exception e){
			available.release();
			e.printStackTrace();	
		}
	}
	
	
	/**
	Tells the computer to begin writing its data to the DB/XML files.
	*/
	public void writeSave(){
		try{
			Loaded=false;
			//Write stuff to the database.
		}catch(Exception e){
			e.printStackTrace();	
		}
	}
	
	/**
	Checks whether the banking port is on before an action can be performed.
	*/
	public boolean checkBank(){
	
		Iterator PortIterator=Ports.entrySet().iterator();
		int ii=0;
		boolean success=false;//MAKE SURE THAT A BANKING PORT IS INSTALLED.
		

		while(PortIterator.hasNext()){
			Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
			if(TempPort.getType()==TempPort.BANKING&&TempPort.getOn()&&!TempPort.getDummy())
				success=true;
			ii++;
		}
		return(success);
	}
	
	/**
	Checks whether a fire wall is installed on any of their ports.
	*/
	public boolean checkFirewall(){
	
		Iterator PortIterator=Ports.entrySet().iterator();
		int ii=0;
		boolean success=false;//MAKE SURE THAT A BANKING PORT IS INSTALLED.

		while(PortIterator.hasNext()){
			Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
			if(TempPort.getFireWall()!=null&&!((String)TempPort.getFireWall().getType().get("name")).equals("None")){
				success=true;
				break;
			}
			ii++;
		}
		return(success);
	}
	
	/**
	Checks whether a mining port is currently on and ready to accept a transaction.
	*/
	public boolean checkShipping(){
	
		Iterator PortIterator=Ports.entrySet().iterator();
		int ii=0;
		boolean success=false;//MAKE SURE THAT A BANKING PORT IS INSTALLED.
		
		while(PortIterator.hasNext()){
			Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
			if(TempPort.getType()==TempPort.SHIPPING&&TempPort.getOn()&&!TempPort.getDummy())
				success=true;
			ii++;
		}
		return(success);
	}
	
	/**
	Checks whether the http port is on before an action can be performed.
	*/
	public boolean checkHTTP(){
		Iterator PortIterator=Ports.entrySet().iterator();
		int ii=0;
		boolean success=false;//MAKE SURE THAT A BANKING PORT IS INSTALLED.
		while(PortIterator.hasNext()){
			Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
			if(TempPort.getType()==TempPort.HTTP&&TempPort.getOn()&&!TempPort.getDummy())
				success=true;
			ii++;
		}
		return(success);
	}
	
	/**
	Checks whether or not any of the ports on this computer are attacking.
	*/
	public boolean getAttacking(){
		Iterator PortIterator=Ports.entrySet().iterator();
		int ii=0;
		boolean success=false;//MAKE SURE THAT A BANKING PORT IS INSTALLED.
		while(PortIterator.hasNext()){
			Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
			if(TempPort.getAttacking())
				return(true);
			ii++;
		}
		return(false);
	}
	
	/**
	Checks whether the http port is on before an action can be performed.
	*/
	public boolean checkFTP(){
		Iterator PortIterator=Ports.entrySet().iterator();
		int ii=0;
		boolean success=false;//MAKE SURE THAT A BANKING PORT IS INSTALLED.
		while(PortIterator.hasNext()){
			Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
			if(TempPort.getType()==TempPort.FTP&&TempPort.getOn()&&!TempPort.getDummy())
				success=true;
			ii++;
		}
		return(success);
	}
	
	/**
	Check whether or not a quest is finished.
	*/
	public boolean checkQuest(int ID){
		for(int i=0;i<CompletedQuests.size();i++){
			int check=(Integer)((Object[])CompletedQuests.get(i))[0];
			if(check==ID)
				return(true);
		}
		return(false);
	}
	
	
	private int getWindowHandle(Port P){
		int windowHandle = 0;
		if(P!=null){
			Program program = P.getProgram();
			if(program instanceof AttackProgram){
				windowHandle = ((AttackProgram)program).getWindowHandle();
			}
			else if(program instanceof ShippingProgram){
				windowHandle = ((ShippingProgram)program).getWindowHandle();
			}
		}
		return(windowHandle);
	}
    
    
	/**
		Check whether the file needs to be renamed.
	
	*/
	
	public HackerFile checkRename(HackerFile HF,String path){
		HackerFile HFCheck = MyFileSystem.getFile(path,HF.getName());
		if(HFCheck!=null){
			if(HFCheck.isStacking()){
				if(HF.checkSumFailed(HFCheck)||(HF.getType()==HF.BOUNTY&&ip.equals(store))){
					String nameCheck = HF.getName();
					int i=0;
					String name=nameCheck+i;
					HackerFile TF=null;
					HF.setName(name);
				  //  System.out.println("Changing name to "+name);
					if(HF.getType()!=HF.BOUNTY){//Check for identical files, bounties are a special case.
						while((TF=MyFileSystem.getFile(path,name))!=null&&HF.checkSumFailed(TF)){
							i++;
					//        System.out.println("Changing name to "+(nameCheck+i));
							name=nameCheck+i;
							HF.setName(name);
						}
					}else{
						while((TF=MyFileSystem.getFile(path,name))!=null){
							i++;
							name=nameCheck+i;
							HF.setName(name);
						}
					}
					
					HF.setName(name);
					if(TF==null||HF.checkSumFailed(TF))
						HFCheck=null;
					else
						HFCheck=TF;
				}
			}
		}
		return(HF);
	}
	
    /**
        Save the file to the file system after checking the checksum, etc.,
        */
    public void saveFile(HackerFile HF,HackerFile HFCheck,String path){
        String nameCheck=HF.getName();
        HF = checkRename(HF,path);
		
        int quantity=HF.getQuantity();
        if(quantity==0)
            quantity=1;
        
        float price=0;
        if(HFCheck!=null){//Does the file already exist on disk?
            if(HFCheck.getQuantity()==-1){
                quantity=-1;
            }else if(HFCheck.isStacking()&&HF.getName().equals(HFCheck.getName())){
                quantity=HFCheck.getQuantity()+HF.getQuantity();
            }
        }
        HF.setQuantity(quantity);
    
        if(!MyFileSystem.addFile(HF,true)){//Check whether the file system can accept a new file.
            addMessage(MessageHandler.HD_FULL);
        }
        
        if(path.equals("Public/")||path.equals("Store/")){
            PA.setRequestSecondary(true,8);
            PA.setRequestPrimary(true,8);
        }else{
            PA.setRequestPrimary(true,1);
        }
        systemChange=true;
    }
	
	/**
		TTJ's method to bypass file quantity calculation and instead set it the the value passed in parameter
		(fix for takeFile bug)
		basically a clone of the saveFile() function
		*/
	private void saveFileTemp(HackerFile HF, HackerFile HFCheck, String path, int newQuantity) {
		String nameCheck=HF.getName();
        HF = checkRename(HF,path);
		
        int quantity=HF.getQuantity();
        if(quantity==0)
            quantity=1;
        
        float price=0;
        if(HFCheck!=null){//Does the file already exist on disk?
            if(HFCheck.getQuantity()==-1){
                quantity=-1;
            }else if(HFCheck.isStacking()&&HF.getName().equals(HFCheck.getName())){
                quantity=HFCheck.getQuantity()+HF.getQuantity();
            }
        }
        HF.setQuantity(newQuantity); // sorry, but i'm ignoring all that crap about quantity
    
        if(!MyFileSystem.addFile(HF,true)){//Check whether the file system can accept a new file.
            addMessage(MessageHandler.HD_FULL);
        }
        
        if(path.equals("Public/")||path.equals("Store/")){
            PA.setRequestSecondary(true,8);
            PA.setRequestPrimary(true,8);
        }else{
            PA.setRequestPrimary(true,1);
        }
        systemChange=true;
	}

	/**
    Fetch execution tasks from the stack.
    */
	private int iterationCount=0;
	private boolean cpuLoadCalculated=false;
    public synchronized void run(){
        while(run){
			iterationCount++;
            long startTime=MyTime.getCurrentTime();
			
			try{
				//LOCK OUR LIST AND POP ONE ENTRY.
				available.acquire();
				//Iterator MyIterator=Tasks.iterator();
				Object o=null;
				//if(MyIterator.hasNext()){
				if(Tasks.size()>0){
					//o=MyIterator.next();
					o=Tasks.get(0);
					if((Loaded&&!LOAD_FAILURE)||!(o instanceof ApplicationData)){
						//MyIterator.remove();
						Tasks.remove(0);
					}
				}
				available.release();
				
				if(!countDown||MyTime.getCurrentTime()-countDownStart<COUNTDOWN_LENGTH)//Has a coundown taken place and the server timed out?
				if(o!=null){
					processQueuedItem(o,startTime);
				}

				
			}catch(Exception e){
				e.printStackTrace();
			}
			runLoopMaintenance(startTime);
        }
        System.out.println("Stopping thread"+ip);
    }
	
	/**
	Generate the CAPTCHA image. (100x20)
	*/
	public static final Object[] generateImage(){
		CaptchaChallenge challenge = new ComputerSessionService().generateCaptcha();
		return new Object[]{challenge.getPixels(), challenge.getKey()};
	}
	

	private void processQueuedItem(Object o,long startTime){
		if(o instanceof Task){
			Task T=(Task)o;
			T.execute();
		}else if(o instanceof ApplicationData&&Loaded){
			boolean checkedWatch=false;
			ApplicationData MyApplicationData=(ApplicationData)o;
			String function=MyApplicationData.getFunction();
			int port=MyApplicationData.getPort();

			updateApplicationActivity(MyApplicationData,function);
			port=normalizeTransferPort(MyApplicationData,function,port);
			maybeLogIncomingMessage(MyApplicationData,function);

			if(commandDispatcher==null)
				buildFunctionHash();

			boolean handledByDispatcher=commandDispatcher!=null&&LegacyRunLoopApplicationDataRouter.dispatch(this,MyApplicationData,port,commandDispatcher);

			if(!handledByDispatcher){
				checkedWatch=dispatchToPortOrFail(MyApplicationData,port);
			}

			finalizeProcessedApplicationData(MyApplicationData,function,startTime,checkedWatch);
		}
	}

	private void updateApplicationActivity(ApplicationData applicationData,String function){
		if(function.equals("ping")){
			lastPingTime = MyTime.getCurrentTime();
			if(logInTime == 0){
				logInTime = MyTime.getCurrentTime();
			}
		}
		
		if(clientPackets.containsKey(function)){
			if(lastClientPacketTime == 0 || logInTime == 0){
				logInTime = MyTime.getCurrentTime();
			}
			lastClientPacketTime = MyTime.getCurrentTime();
			int lockCountAdd = (Integer)clientPackets.get(function);
			lockCount+=lockCountAdd;
		}

		if(applicationData.getSource()==ApplicationData.OUTSIDE)
			GUI_READY=true;
	}

	private int normalizeTransferPort(ApplicationData applicationData,String function,int port){
		if(function.equals("requestsecondarydirectory")||function.equals("put")||function.equals("get")||function.equals("finalizeput")){
			String targetIP=(String)((Object[])applicationData.getParameters())[0];

			if(!targetIP.equals(ip)){
				port=defaultFTP;
				Port P=(Port)Ports.get(new Integer(port));
				if(P!=null){
					if(P.getType()!=P.FTP){
						MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.PORT_WAS_NOT_FTP,new Object[]{port,ip}},0,ip),targetIP);
						if(function.equals("finalizeput"))
							P.friendlyPut(applicationData);
					}else if(P.getDummy()){
						MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.PORT_WAS_DUMMY,new Object[]{port,ip},new Object[]{applicationData.getSourcePort(),targetIP}},0,ip),targetIP);
						if(function.equals("finalizeput"))
							P.friendlyPut(applicationData);
					}else if(!P.getOn()){
						MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.PORT_NOT_ON,new Object[]{port,ip}},0,ip),targetIP);
						if(function.equals("finalizeput"))
							P.friendlyPut(applicationData);
					}

				}else{
					if(function.equals("finalizeput"))
						P.friendlyPut(applicationData);
					MyComputerHandler.addData(new ApplicationData("message",MessageHandler.FTP_NOT_FOUND,0,ip),targetIP);
				}
			}
		}
		return(port);
	}

	private void maybeLogIncomingMessage(ApplicationData applicationData,String function){
		if(function.equals("logmessage")){
			String message = (String)((Object[])applicationData.getParameters())[0];
			String ip = (String)((Object[])applicationData.getParameters())[1];
			Long timestamp = (Long)((Object[])applicationData.getParameters())[2];
			logMessage(message,ip,timestamp);
		}
	}

	private boolean dispatchToPortOrFail(ApplicationData applicationData,int port){
		if(Ports.get(new Integer(port))!=null){
			Port tempport=(Port)Ports.get(new Integer(port));
			tempport.setCurrentPacket(PA);
			tempport.addApplicationData(applicationData,MyTime.getCurrentTime());
			
			currentWatchCost=MyWatchHandler.checkWatches(applicationData,Ports,pettyCash);
			return(true);
		}else{
			if(applicationData.getFunction().equals("damage"))
				MyComputerHandler.addData(new ApplicationData("requestcancelattack",null,applicationData.getSourcePort(),this.getIP()),applicationData.getSourceIP());

			if(!applicationData.getSourceIP().equals(ip))
				MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.PORT_NOT_ON,new Object[]{port,ip}},0,ip),applicationData.getSourceIP());
			addMessage(MessageHandler.COULD_NOT_EXECUTE_APPLICATION,new Object[]{port});
			systemChange=true;
			return(false);
		}
	}

	private void finalizeProcessedApplicationData(ApplicationData applicationData,String function,long startTime,boolean checkedWatch){
		if(!function.equals("requestequipment")){
			lastAccessed=startTime;
		}
		if(!checkedWatch)
			currentWatchCost=MyWatchHandler.checkWatches(applicationData,Ports,pettyCash);
	}

	private void runLoopMaintenance(long startTime){
		//Check whether or not a packet should currently be sent.
		sendStandardPacket();
		runRuntimeCoordinatorTick();
		
		//Macro Protection.
		if(operationCount>6000&&!isNPC()){
			RawComputerHandler.broadcast(new ApplicationData("message",new Object[]{MessageHandler.PLAYER_BUSY,new Object[]{ip}},0,""));
			operationCount=0;
		}
		
		//Sleep to cut down on processor load.
		if(Tasks.size()==0){
			try{
				long endTime=MyTime.getCurrentTime();
				if(SLEEP_TIME-(endTime-startTime)>0)
					MyThread.sleep(SLEEP_TIME-(endTime-startTime));
			}catch(InterruptedException e){
				if(run){
					e.printStackTrace();
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}else{
			if(Tasks.size()>10){
				CentralLogging.getInstance().addOutput("Username: "+userName+" IP:"+ip+" Spamming?\n");
			}
		}
		
		//Dispatch a 3D chat update.
		if(getLoaded()&&!getLoading()&&type!=NPC&&GUI_READY)
			sendChatPacket();
		
		try{
			MyThread.sleep(50);
		}catch(InterruptedException e){
			if(run){
				e.printStackTrace();
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	private void runRuntimeCoordinatorTick(){
		long now=MyTime.getCurrentTime();
		checkRuntimePortTimeouts(now);
		RuntimeTickState runtimeState=buildRuntimeTickState(now);
		RuntimeTickEventApplier.INSTANCE.apply(runtimeCoordinator.tick(runtimeState),buildRuntimeTickEventSink(now));
		applyRuntimeTickState(runtimeState);
	}

	private void checkRuntimePortTimeouts(long now){
		Iterator portIterator=Ports.entrySet().iterator();
		while(portIterator.hasNext()){
			Port tempPort=(Port)(((Map.Entry)portIterator.next()).getValue());
			tempPort.checkTimeOut(now);
		}
	}

	private RuntimeTickState buildRuntimeTickState(final long now){
		final RuntimeTickState state=new RuntimeTickState();
		state.setNow(now);
		state.setIp(ip);
		state.setLoaded(Loaded);
		state.setLoading(Loading);
		state.setLoggedIn(loggedIn);
		state.setLoadFailure(LOAD_FAILURE);
		state.setLogoutRequested(LOGOUT);
		state.setCountDown(countDown);
		state.setCountDownStart(countDownStart);
		state.setCountDownLengthMs(COUNTDOWN_LENGTH);
		state.setLastAccessed(lastAccessed);
		state.setComputerTimeoutMs(COMPUTER_TIMEOUT);
		state.setLastSave(lastSave);
		state.setAutoSaveMs(AUTO_SAVE);
		state.setLoadRequester(loadRequester);
		state.setErrorMessage(errorMessage);
		state.getPendingTasks().addAll(buildRuntimeQueuedTasks());
		state.setLastPingTime(lastPingTime);
		state.setLogInTime(logInTime);
		state.setLastClientPacketTime(lastClientPacketTime);
		state.setPingTimeoutMs(PING_TIMEOUT);
		state.setClientPacketTimeoutMs(CLIENT_PACKET_TIMEOUT);
		state.setLastPaid(lastPaid);
		state.setPayPeriodMs(PAY_PERIOD);
		state.setDailyPaySize(dailyPaySize);
		state.setDailyPayReduction(dailyPayReduction);
		state.setInactive(inactive);
		state.setNpc(isNPC());
		state.setType(type);
		state.setHttpActive(checkHTTP());
		state.setHttpLevel(getHTTPLevel());
		state.setPettyCash(pettyCash);
		state.setBankMoney(bankMoney);
		state.setMyVotes(myVotes);
		state.setAdRevenueTarget(adRevenueTarget);
		state.setCurrentCPU(currentCPU);
		state.setReportCPU(reportCPU);
		state.setBaseCPU(baseCPU);
		state.setCurrentWatchCost(currentWatchCost);
		state.setCpuLoadCalculated(cpuLoadCalculated);
		state.setLastAttack(lastAttack);
		state.setAttackRateMs(ATTACK_RATE);
		state.setHealCounter(healCounter);
		state.setHealMod(MyEquipmentSheet.getHealMod());
		state.setOverheatStart(overheatStart);
		state.setOverHeatTimeMs(OVER_HEAT_TIME);
		state.setSentOverHeatedMessage(sentOverHeatedMessage);
		state.setCpuMaximum(CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus());
		state.setLockCount(lockCount);
		state.setLocked(locked);
		state.setResendCaptcha(RESEND_CAPTCHA);
		state.setCaptchaThreshold(CAPTCHA_COUNT);
		state.setUnlockKey(unlockKey);
		state.setOperationCount(operationCount);
		state.setGrantFilesInterval(150);
		state.setGrantFilesCounter(Math.max(0,iterationCount-1));
		state.setCaptchaGenerator(new Function0<RuntimeCaptchaPayload>(){
			public RuntimeCaptchaPayload invoke(){
				Object[] generated=Computer.generateImage();
				int[] pixels=(generated[0] instanceof int[])?(int[])generated[0]:new int[0];
				return new RuntimeCaptchaPayload((String)generated[1],pixels);
			}
		});
		state.setWatchCostSupplier(new Function0<Float>(){
			public Float invoke(){
				return MyWatchHandler.checkWatches(new ApplicationData("null",null,0,ip),Ports,pettyCash);
			}
		});
		state.getPorts().addAll(buildRuntimePortSnapshots());
		return(state);
	}

	private List buildRuntimeQueuedTasks(){
		ArrayList runtimeTasks=new ArrayList();
		ArrayList queuedItems=new ArrayList(Tasks);
		Iterator iterator=queuedItems.iterator();
		while(iterator.hasNext()){
			Object queued=iterator.next();
			if(queued instanceof ApplicationData){
				ApplicationData applicationData=(ApplicationData)queued;
				runtimeTasks.add(new RuntimeQueuedTask(
					applicationData.getFunction(),
					applicationData.getSourceIP(),
					applicationData.getParameters(),
					applicationData.getPort(),
					applicationData.getSourcePort(),
					applicationData.getSource()
				));
			}
		}
		return(runtimeTasks);
	}

	private List buildRuntimePortSnapshots(){
		ArrayList snapshots=new ArrayList();
		Iterator portIterator=Ports.entrySet().iterator();
		while(portIterator.hasNext()){
			Port tempPort=(Port)(((Map.Entry)portIterator.next()).getValue());
			snapshots.add(
				new RuntimePortSnapshot(
					tempPort.getNumber(),
					tempPort.getType(),
					tempPort.getOn(),
					tempPort.getDummy(),
					tempPort.getAttacking(),
					tempPort.getOverHeated(),
					tempPort.getHealth(),
					tempPort.getCPUCost(),
					tempPort.getBaseCPUCostTotal(),
					tempPort.getLastDamageWindowHandle(),
					tempPort.getAccessing(),
					getRuntimeTargetPort(tempPort),
					getRuntimeTargetIP(tempPort),
					getRuntimeMaliciousTarget(tempPort),
					isRuntimeZombie(tempPort)
				)
			);
		}
		return(snapshots);
	}

	private int getRuntimeTargetPort(Port tempPort){
		if(tempPort.getProgram() instanceof AttackProgram){
			return((AttackProgram)tempPort.getProgram()).getTargetPort();
		}else if(tempPort.getProgram() instanceof ShippingProgram){
			return((ShippingProgram)tempPort.getProgram()).getTargetPort();
		}
		return(-1);
	}

	private String getRuntimeTargetIP(Port tempPort){
		if(tempPort.getProgram() instanceof AttackProgram){
			String targetIP=((AttackProgram)tempPort.getProgram()).getTargetIP();
			return(targetIP==null?"":targetIP);
		}else if(tempPort.getProgram() instanceof ShippingProgram){
			String targetIP=((ShippingProgram)tempPort.getProgram()).getTargetIP();
			return(targetIP==null?"":targetIP);
		}
		return("");
	}

	private String getRuntimeMaliciousTarget(Port tempPort){
		if(tempPort.getProgram() instanceof AttackProgram){
			String maliciousIP=((AttackProgram)tempPort.getProgram()).getMaliciousIP();
			if(maliciousIP!=null)
				return(maliciousIP);
		}
		return(tempPort.getMaliciousTarget());
	}

	private boolean isRuntimeZombie(Port tempPort){
		if(tempPort.getProgram() instanceof AttackProgram){
			return(((AttackProgram)tempPort.getProgram()).isZombie());
		}
		return(false);
	}

	private RuntimeTickEventSink buildRuntimeTickEventSink(final long now){
		return new RuntimeTickEventSink(){
			public void persistRequested(boolean autoSave){
				if(autoSave){
					MyEquipmentSheet.degradeEquipment();
				}
				try{
					MysqlHandler.addWork(new Object[]{ip,Computer.this,"asdbas0d98a0sd9fa8sasdlbo",new Boolean(pageChanged),pageTitle,pageBody});
				}catch(Exception e){
					e.printStackTrace();
				}
				if(autoSave){
					MyComputerHandler.addData(new ApplicationData("requestequipment",new Integer(13),0,ip),ip);
				}
			}

			public void unloadRequested(){
				Loaded=false;
				RawComputerHandler.addData(null,ip);
			}

			public void playerCountDecrementRequested(){
				RawComputerHandler.decrementPlayers();
			}

			public void applicationDataDispatchRequested(game.computer.runtime.RuntimeApplicationDataDispatch runtimeApplicationData,String targetIp){
				ApplicationData applicationData=new ApplicationData(runtimeApplicationData.getFunction(),runtimeApplicationData.getParameters(),runtimeApplicationData.getPort(),runtimeApplicationData.getSourceIp());
				applicationData.setSourcePort(runtimeApplicationData.getSourcePort());
				applicationData.setSource(runtimeApplicationData.getSource());
				MyComputerHandler.addData(applicationData,targetIp);
			}

			public void logEntry(String message,String targetIP,long timestamp){
				logMessage(message,targetIP,timestamp);
			}

			public void playSessionRecorded(String targetIP,long startedAt,long endedAt){
				sessionService.recordPlayWindow(targetIP,startedAt,endedAt);
			}

			public void dailyPayIssued(float amount,String targetIP){
				MyComputerHandler.addData(new ApplicationData("pettycash",new Object[]{new Float(amount),false},0,ip),targetIP);
			}

			public void httpXpIssued(float amount,String targetIP){
				MyComputerHandler.addData(new ApplicationData("httpxp",new Float(amount),0,ip),targetIP);
			}

			public void bankMoneyAdded(float amount){
			}

			public void pettyCashAdded(float amount){
			}

			public void attackContinueRequested(int portNumber,int targetPort){
				Port tempPort=(Port)Ports.get(new Integer(portNumber));
				if(tempPort!=null){
					tempPort.addApplicationData(new ApplicationData("attackcontinue",null,targetPort,""),now);
				}
			}

			public void overheatAnnounced(String targetIP){
				addMessage(MessageHandler.COMPUTER_OVERHEATED);
			}

			public void opponentOverheated(String targetIP,int windowHandle,String accessing){
				MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.OVERHEATED_OPPONENT,new Object[]{ip},new Object[]{windowHandle,accessing}},0,ip),targetIP);
				MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.OVERHEATED_OPPONENT_GAME,new Object[]{ip}},0,ip),targetIP);
			}

			public void zombieOverheated(String targetIP,String maliciousIp){
				MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.ZOMBIE_OVERHEATED,new Object[]{targetIP}},0,targetIP),maliciousIp);
			}

			public void captchaRequested(RuntimeCaptchaPayload payload){
				PA.setCAPTCHA(payload.getImage());
				unlockKey=payload.getUnlockKey();
				sendPacket();
			}

			public void grantFilesRequested(){
				GiveItemsSingleton.getInstance().giveFiles(Computer.this,RawComputerHandler);
			}

			public void equipmentRefreshRequested(){
				healthChange=true;
			}
		};
	}

	private void applyRuntimeTickState(RuntimeTickState runtimeState){
		applyRuntimePortSnapshots(runtimeState);
		Loaded=runtimeState.getLoaded();
		lastSave=runtimeState.getLastSave();
		lastPingTime=runtimeState.getLastPingTime();
		logInTime=runtimeState.getLogInTime();
		lastClientPacketTime=runtimeState.getLastClientPacketTime();
		lastPaid=runtimeState.getLastPaid();
		inactive=runtimeState.getInactive();
		pettyCash=runtimeState.getPettyCash();
		bankMoney=runtimeState.getBankMoney();
		myVotes=runtimeState.getMyVotes();
		currentCPU=runtimeState.getCurrentCPU();
		reportCPU=runtimeState.getReportCPU();
		baseCPU=runtimeState.getBaseCPU();
		currentWatchCost=runtimeState.getCurrentWatchCost();
		cpuLoadCalculated=runtimeState.getCpuLoadCalculated();
		lastAttack=runtimeState.getLastAttack();
		healCounter=runtimeState.getHealCounter();
		overheatStart=runtimeState.getOverheatStart();
		sentOverHeatedMessage=runtimeState.getSentOverHeatedMessage();
		lockCount=runtimeState.getLockCount();
		locked=runtimeState.getLocked();
		RESEND_CAPTCHA=runtimeState.getResendCaptcha();
		unlockKey=runtimeState.getUnlockKey();
		iterationCount=runtimeState.getGrantFilesCounter();
	}

	private void applyRuntimePortSnapshots(RuntimeTickState runtimeState){
		Iterator iterator=runtimeState.getPorts().iterator();
		while(iterator.hasNext()){
			RuntimePortSnapshot runtimePort=(RuntimePortSnapshot)iterator.next();
			Port tempPort=(Port)Ports.get(new Integer(runtimePort.getNumber()));
			if(tempPort==null)
				continue;

			float oldHealth=tempPort.getHealth();
			float newHealth=runtimePort.getHealth();
			if(newHealth!=oldHealth){
				if(tempPort.damagePort(oldHealth-newHealth)){
					healthChange=true;
					MyWatchHandler.updateInitialHealthQuanity(tempPort.getNumber(),tempPort.getHealth());
				}
			}

			if(tempPort.getOverHeated()!=runtimePort.getOverHeated()){
				tempPort.setOverHeated(runtimePort.getOverHeated());
			}

			if(tempPort.getAttacking()!=runtimePort.getAttacking()){
				tempPort.setAttacking(runtimePort.getAttacking());
			}
		}
	}

	/**
	Run the logic used to perform the saving process of accounts.
	*/
	public void runSavingLogic(){
		//System.out.println("Running Saving Logic");
		//WRITE THE COMPUTER BACK TO DISK WHEN A TIMEOUT IS REACHED.
		//System.out.println("Computer Timeout: "+COMPUTER_TIMEOUT+" Logged Time:"+(MyTime.getCurrentTime()-lastAccessed));
		if((MyTime.getCurrentTime()-lastAccessed>COMPUTER_TIMEOUT||LOGOUT||LOAD_FAILURE||(countDown&&MyTime.getCurrentTime()-countDownStart>COUNTDOWN_LENGTH))&&Loaded){
			//Message the player who requested this load with the error message.
			if(!loadRequester.equals(ip)&&LOAD_FAILURE&&!loadRequester.equals("")){
				Iterator MyIterator=Tasks.iterator();
				Object o=null;
				if(MyIterator.hasNext()){
					o=MyIterator.next();
					if(o instanceof ApplicationData){
						ApplicationData AD=(ApplicationData)o;
						if(AD.getFunction().equals("pettycash")){
							MyComputerHandler.addData(AD,AD.getSourceIP());
						}
						
						if(AD.getFunction().equals("requestwebpage")){
							String PageTitle="Server Not Found";
							String PageBody="<html><head><title>Hack Wars - Error report</title><style><!--H1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;color:white} H2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} H3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} BODY {background-color:rgb(0,0,0);font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;color:white;} B {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;color:white;} P {color:white;font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;}A {color : black;}A.name {color : black;}HR {color : #525D76;}--></style> </head><body><h1 style=\"width:100%\">HTTP Status 408</h1><HR size=\"1\" noshade=\"noshade\"><p style=\"background-color:black;\"><b>type</b> HTTP Error</p><p style=\"background-color:black;\"><b>message</b> <u>Resource not found.</u></p><p style=\"background-color:black\"><b>description</b> <u>The HTTP server of the player you attempted to connect to does not seem to be on.</u></p><HR size=\"1\" noshade=\"noshade\"><h3>&copy; Hack Wars</h3></body></html>";
							Object[] Files=null;
							Object O[]=new Object[]{PageTitle,PageBody,Files,0};
							MyComputerHandler.addData(new ApplicationData("webpage",O,0,ip),AD.getSourceIP());
						}
					}
					MyIterator.remove();
				}
				
			
				MyComputerHandler.addData(new ApplicationData("message",errorMessage,0,ip),loadRequester);
			}

			if(!LOAD_FAILURE){//Only write to disk if the file didn't fail to load.
				try{
					MysqlHandler.addWork(new Object[]{ip,this,"asdbas0d98a0sd9fa8sasdlbo",new Boolean(pageChanged),pageTitle,pageBody});
				}catch(Exception e){
					e.printStackTrace();
				}

			}
							
			Loaded=false;
			RawComputerHandler.addData(null,ip);
			if(type!=NPC)
				RawComputerHandler.decrementPlayers();
		}else if(!LOAD_FAILURE){//Perform an auto-save every 10 minutes or so.
			if(lastSave==0)
				lastSave=MyTime.getCurrentTime();
			if(MyTime.getCurrentTime()-lastSave>AUTO_SAVE){
				MyEquipmentSheet.degradeEquipment();//This is a good time to check whether or not equipment has degraded.
							
				lastSave=MyTime.getCurrentTime();
				try{
					MysqlHandler.addWork(new Object[]{ip,this,"asdbas0d98a0sd9fa8sasdlbo",new Boolean(pageChanged),pageTitle,pageBody});
				}catch(Exception e){
					e.printStackTrace();
				}
				MyComputerHandler.addData(new ApplicationData("requestequipment",new Integer(13),0,ip),ip);
			}
		}
	}
	
	/**
	Checks wheter or not we have stopped getting a ping and therefore need to write to a db how long the player has been playing.
	*/
	public void checkPingTime(){
		game.computer.session.PlayStatisticsResult result = sessionService.recordPlayStatistics(
			new PlayStatisticsRequest(
				loggedIn,
				ip,
				MyTime.getCurrentTime(),
				logInTime,
				lastPingTime,
				lastClientPacketTime
			)
		);
		lastPingTime = result.getLastPingTimeMillis();
		logInTime = result.getLogInTimeMillis();
		lastClientPacketTime = result.getLastClientPacketTimeMillis();
	}
		
	/**
	Checks whether or not the player should be given their daily pay and provides it.
	*/
	public void checkDailyPay(){
		/**
		CHECK WHETHER IT IS TIME FOR DAILY PAY AND PROVIDE IT TO THE AD REVENUE TARGET.
		*/
		if(lastPaid<=100)//Make sure that the first time the player plays they don't get paid.
			lastPaid=MyTime.getCurrentTime();

		if(MyTime.getCurrentTime()-lastPaid>PAY_PERIOD&&Loaded&&!inactive){
			Port TempPort=null;
			
			myVotes+=1;//Get some more votes.
			if(myVotes>4)
				myVotes=4;
			
			if(!checkHTTP()){
				logMessage("Did not receive income from website because HTTP is not installed.",ip,lastPaid+PAY_PERIOD);
				lastPaid = MyTime.getCurrentTime();
			}else{
				float mod=(getHTTPLevel()-1.0f)*50.0f;
				if(type==NPC)
					mod=0.0f;
					
				float amount=(dailyPaySize+mod)*0.75f*dailyPayReduction;
				float extra = (dailyPaySize+mod)*0.75f-amount;
				if(extra>0.0f){
					pettyCash+=extra;
					String message="Transferred "+NumberFormat.getCurrencyInstance().format(extra)+" of daily pay from "+ip+".";
					logMessage(message,ip,new Long(lastPaid+PAY_PERIOD));
				}
				
				ApplicationData MyPettyCash=new ApplicationData("pettycash",new Object[]{new Float(amount),false},0,ip);
				MyComputerHandler.addData(MyPettyCash,adRevenueTarget);
				ApplicationData MyHTTPXP = new ApplicationData("httpxp",new Float(getHTTPLevel()*10.0f),0,ip);
				MyComputerHandler.addData(MyHTTPXP,adRevenueTarget);
				String message="Transferred "+NumberFormat.getCurrencyInstance().format(amount)+" of daily pay from "+ip+".";
				Object[] logMessageParameters = new Object[]{message,ip,lastPaid+PAY_PERIOD};
				MyPettyCash=new ApplicationData("logmessage",logMessageParameters,0,ip);
				MyComputerHandler.addData(MyPettyCash,adRevenueTarget);
				message = "Received $"+(dailyPaySize+mod)*0.25+" in guaranteed income to bank.";
				logMessageParameters = new Object[]{message,ip,lastPaid+PAY_PERIOD};
				MyPettyCash=new ApplicationData("logmessage",logMessageParameters,0,ip);
				MyComputerHandler.addData(MyPettyCash,ip);
				bankMoney += (dailyPaySize+mod)*0.25;
				lastPaid+=PAY_PERIOD;
			}
			
		}
		else if(inactive){
			logMessage("Did not receive income because you were inactive.",ip,MyTime.getCurrentTime());
			lastPaid=MyTime.getCurrentTime();
			inactive = false;
		}
	}
	
	/**
	This function runs the attack logic, this takes care of dealing with overheating, applying hardware, and calculating
	current CPU costs.
	*/
	public void runAttackLogic(){
			/**
			CHECK FOR ATTACKS/PERFORM HEALING AT THE GIVEN RATE/CALCULATE CPU LOAD.
			Deals with: Attacking, Healing, Over Heating.
			*/
			if(getLoaded()&&!getLoading()&&MyTime.getCurrentTime()-lastAttack>ATTACK_RATE){			
				float startReportCPU=reportCPU;
				
				if(!cpuLoadCalculated)//Check the current watch cost.
					currentWatchCost=MyWatchHandler.checkWatches(new ApplicationData("null",null,0,ip),Ports,pettyCash);
				cpuLoadCalculated=true;
				
				float startCPU=currentCPU;//What was the CPU cost at the start?
			
				//Heal the port at a given rate -- at this time once every 6 seconds.
				boolean heal=false;
				boolean overHeated=false;
				if(healCounter%MyEquipmentSheet.getHealMod()==0)
					heal=true;
				if(currentCPU>CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus()){
					overHeated=true;
					if(overheatStart==-1)
						overheatStart=MyTime.getCurrentTime();
				}else if(MyTime.getCurrentTime()-overheatStart>OVER_HEAT_TIME&&overheatStart!=-1){
					overheatStart=-1;
				}else if(overheatStart!=-1){
					overHeated=true;
				}
			
				Iterator PortIterator=Ports.entrySet().iterator();
				int ii=0;
				float tempCPULoad=0.0f;
				float tempBaseCPULoad=0.0f;
				while(PortIterator.hasNext()){
					Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
					
					//If heal true heal the port.
					if(heal){
						if(TempPort.damagePort(-1.0f)){
							healthChange=true;
							MyWatchHandler.updateInitialHealthQuanity(TempPort.getNumber(),TempPort.getHealth());
						}
					}
					//Put the ports into over-heat mode.
					if(overHeated){
						if(!TempPort.getOverHeated()&&TempPort.getHealth()!=100){
							if(TempPort.getType()==Port.ATTACK||TempPort.getType()==Port.SHIPPING){
								if(TempPort.getProgram() instanceof AttackProgram){
									AttackProgram TempProgram=(AttackProgram)TempPort.getProgram();
									if(TempProgram.isZombie())
										MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.ZOMBIE_OVERHEATED,new Object[]{ip}},0,ip),TempProgram.getMaliciousIP());
									//send a message to the other player that they are overheated.
									MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.OVERHEATED_OPPONENT,new Object[]{ip},new Object[]{TempPort.getLastDamageWindowHandle(),TempPort.getAccessing()}},0,ip),TempProgram.getTargetIP());
									MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.OVERHEATED_OPPONENT_GAME,new Object[]{ip}},0,ip),TempProgram.getTargetIP());
								}
							}
							if(!sentOverHeatedMessage){
								addMessage(MessageHandler.COMPUTER_OVERHEATED);
								sentOverHeatedMessage = true;
							}
						}
					
						TempPort.setOverHeated(true);//Put the port in an overheated state.
					}
					
					TempPort.checkTimeOut(MyTime.getCurrentTime());//Has the port timed out since it was attacked.
					
					tempCPULoad+=TempPort.getCPUCost();
					tempBaseCPULoad+=TempPort.getBaseCPUCostTotal();
					
					if(TempPort.getOn()==false){//For test.
						TempPort.setAttacking(false);
					}
					
					if((TempPort.getType()==Port.ATTACK||TempPort.getType()==Port.SHIPPING)&&TempPort.getAttacking()){//If a port is attacking force the attack to continue.
						if(TempPort.getProgram() instanceof AttackProgram){
							AttackProgram AP=(AttackProgram)TempPort.getProgram();
							TempPort.addApplicationData(new ApplicationData("attackcontinue",null,AP.getTargetPort(),""),MyTime.getCurrentTime());
						}else{
							ShippingProgram SP=(ShippingProgram)TempPort.getProgram();
							TempPort.addApplicationData(new ApplicationData("attackcontinue",null,SP.getTargetPort(),""),MyTime.getCurrentTime());
						}
					}
					ii++;
				}
				
				baseCPU=tempBaseCPULoad+currentWatchCost;//The base CPU prior to overheating.
				currentCPU=tempCPULoad+currentWatchCost;
				lastAttack=MyTime.getCurrentTime();
				healCounter++;
								
				reportCPU=currentCPU;
				if(overHeated&&currentCPU<=CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus()){
					reportCPU=CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus()+1;
				}else if(!overHeated){//Make sure the ports do not think they're overheated.
					PortIterator=Ports.entrySet().iterator();
					while(PortIterator.hasNext()){
						Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
						TempPort.setOverHeated(false);
						sentOverHeatedMessage = false;
					}
				}
									
				if(currentCPU!=startCPU||startReportCPU!=reportCPU)//The CPU Load has Changed.
					healthChange=true;
					
			}else{//Otherwise make sure we still calculate the CPU load.
				currentCPU=0.0f;
				Iterator PortIterator=Ports.entrySet().iterator();
				boolean overHeated=false;
				while(PortIterator.hasNext()){
					Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
					if(TempPort!=null){
						currentCPU+=TempPort.getCPUCost();
						if(TempPort.getOverHeated()){
							overHeated=true;
						}
					}
				}
				currentCPU+=currentWatchCost;
				
				if(overHeated&&currentCPU<=CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus())
					reportCPU=CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus()+1;
				else
					reportCPU=currentCPU;

			}
	}
	
	/**
	This function checks whether or not a packet should currently be sent, be it a damage packet or a standard packet.
	*/
	public void sendStandardPacket(){
		/**
		AT THE END OF THE PACKET TIMEOUT DISPATCH A PACKET TO THE CLIENT.
		*/
		if(systemChange||healthChange)
		if(cpuLoadCalculated&&getLoaded()&&!getLoading()&&MyTime.getCurrentTime()-lastSent>PACKET_TIMEOUT&&connectionID>=0){
			
			if(systemChange){
			
				systemChange=false;
				standardPacketBuilder.populate(PA, buildStandardPacketSnapshot());
				Messages.clear();
				Choices.clear();
				if(LOG_UPDATE){
					LOG_UPDATE=false;
				}
				if(sendPreferences){
					sendPreferences = false;
				}
				
				Object O[]=new Object[]{PA,new Integer(connectionID)};

				MyHackerServer.addData(O);
				lastSent=MyTime.getCurrentTime();
				PA=new PacketAssignment(0);
			}
			
			if(healthChange){
				healthChange=false;
				damagePacketBuilder.populate(DA, buildDamagePacketSnapshot());
				Damage.clear();
						
				Object O[]=new Object[]{DA,new Integer(connectionID)};
				MyHackerServer.addData(O);
				lastSent=MyTime.getCurrentTime();
				DA=new DamageAssignment(0);
			}
		}
	}

	private ComputerStandardPacketSnapshot buildStandardPacketSnapshot(){
		Object[] messageArray = Messages.toArray();
		ArrayList<Object[]> choices = new ArrayList<Object[]>();
		Iterator choiceIterator = Choices.iterator();
		while(choiceIterator.hasNext()){
			choices.add((Object[])choiceIterator.next());
		}

		ArrayList<String[]> logMessages = null;
		if(LOG_UPDATE){
			logMessages = new ArrayList<String[]>();
			Iterator logIterator = LogMessages.iterator();
			while(logIterator.hasNext()){
				logMessages.add((String[])logIterator.next());
			}
		}

		HashMap<String, Object> preferenceCopy = null;
		if(sendPreferences&&preferences!=null){
			preferenceCopy = new HashMap<String, Object>();
			preferenceCopy.putAll(preferences);
		}

		Integer countDownSeconds = null;
		if(countDown){
			countDownSeconds = (int)((COUNTDOWN_LENGTH-(MyTime.getCurrentTime()-countDownStart))/1000);
		}

		return new ComputerStandardPacketSnapshot(
			pettyCash,
			bankMoney,
			CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus(),
			cputype,
			memorytype,
			defaultBank,
			defaultAttack,
			defaultHTTP,
			defaultFTP,
			defaultShipping,
			successfulHacks,
			voteCount,
			reportCPU,
			MyFileSystem.getHDType(),
			MyFileSystem.getQuantity()-2,
			MyFileSystem.getMaximumSpace(),
			RawComputerHandler.getPlayers(),
			commodityAmount,
			MyEquipmentSheet.getHealBonus(),
			myVotes,
			messageArray,
			choices,
			logMessages,
			countDownSeconds,
			preferenceCopy
		);
	}

	private ComputerDamagePacketSnapshot buildDamagePacketSnapshot(){
		ArrayList<PortHealthSnapshot> healthUpdates = new ArrayList<PortHealthSnapshot>();
		Iterator portIterator = Ports.entrySet().iterator();
		while(portIterator.hasNext()){
			Port tempPort=(Port)(((Map.Entry)portIterator.next()).getValue());
			healthUpdates.add(new PortHealthSnapshot(
				tempPort.getNumber(),
				tempPort.getHealth(),
				tempPort.getCPUCost(),
				tempPort.getFireWall().getType(),
				tempPort.getHealCount(),
				tempPort.getBaseCPUCostAndFirewall(),
				getWindowHandle(tempPort)
			));
		}

		ArrayList<Object[]> damageEntries = new ArrayList<Object[]>();
		Iterator damageIterator = Damage.iterator();
		while(damageIterator.hasNext()){
			damageEntries.add((Object[])damageIterator.next());
		}

		return new ComputerDamagePacketSnapshot(
			getStatXP("Attack"),
			getStatXP("Bank"),
			getStatXP("FireWall"),
			getStatXP("Watch"),
			getStatXP("Scanning"),
			getStatXP("Webdesign"),
			getStatXP("Redirecting"),
			getStatXP("Repair"),
			reportCPU,
			healthUpdates,
			damageEntries
		);
	}
	
	/**
	This function takes care of creating a 3D chat packet and dispatching it.
	*/
	public void sendChatPacket(){
		/*HacktendoPacket HP=WorldSingleton.getInstance().getPacket("game",ip);
		if(HP.getSpriteEvents().size()>0){
			Object O[]=new Object[]{HP,new Integer(connectionID)};
			MyHackerServer.addData(O);
		}*/
	}
	
	//Testing main.
	public static void main(String args[]){
	}
	
	
	//
	//
	////////////////// WHAT FOLLOWS IS THE LOADING AND SAVING STEPS EXCLUSIVELY.
	

	/**
	This task loads the save file representing the computer from disk.
	*/	
	private class loadSaveTask implements Task{
		private Computer MyComputer=null;
		private boolean run=false;
		
			public loadSaveTask(Computer MyComputer){
				this.MyComputer=MyComputer;
			}
			public void execute(){
				if(!run){
					run=true;
					loadCoordinator.execute(MyComputer);
				}
			}
		}
	
	
	
	
	/**
	Output the contents of this class as an XML string.
	*/
	public String outputXML() throws Exception{
		String returnMe="";
		try{
			returnMe = persistenceSupport.outputXml(this);
		}catch(Exception e){
			try{
				BufferedWriter Out=new BufferedWriter(new FileWriter("saveerror.txt",true));
				Out.write(returnMe);
				Out.write(e.toString());
				Out.close();
			}catch(Exception e2){
				e.printStackTrace();
			}
			throw(e);
		}
		return(returnMe);
	}
    
    /**
	This function encapsulates the loading of the file data-structure.
	*/
    public HackerFile loadFile(Node N,LoadXML LX){
        return persistenceSupport.loadFile(N,LX);      
    }
}
